package io.sentry.cache;

import static io.sentry.SentryLevel.ERROR;
import static io.sentry.SentryLevel.INFO;
import static io.sentry.cache.CacheUtils.ensureCacheDir;

import io.sentry.Breadcrumb;
import io.sentry.IScope;
import io.sentry.ScopeObserverAdapter;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.SpanContext;
import io.sentry.protocol.Contexts;
import io.sentry.protocol.Request;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.User;
import io.sentry.util.LazyEvaluator;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public final class PersistingScopeObserver extends ScopeObserverAdapter {

  /** Sentinel value marking a file that should be deleted rather than written on the next flush. */
  private static final Object DELETE_MARKER = new Object();

  public static final String SCOPE_CACHE = ".scope-cache";
  public static final String USER_FILENAME = "user.json";
  public static final String BREADCRUMBS_FILENAME = "breadcrumbs.json";
  public static final String TAGS_FILENAME = "tags.json";
  public static final String EXTRAS_FILENAME = "extras.json";
  public static final String CONTEXTS_FILENAME = "contexts.json";
  public static final String REQUEST_FILENAME = "request.json";
  public static final String LEVEL_FILENAME = "level.json";
  public static final String FINGERPRINT_FILENAME = "fingerprint.json";
  public static final String TRANSACTION_FILENAME = "transaction.json";
  public static final String TRACE_FILENAME = "trace.json";
  public static final String REPLAY_FILENAME = "replay.json";

  private @NotNull SentryOptions options;
  private final @NotNull LazyEvaluator<BreadcrumbAppendLog> breadcrumbsLog =
      new LazyEvaluator<>(
          () -> {
            final File cacheDir = ensureCacheDir(options, SCOPE_CACHE);
            if (cacheDir == null) {
              options.getLogger().log(INFO, "Cache dir is not set, cannot store in scope cache");
              return new BreadcrumbAppendLog(options, null);
            }
            return new BreadcrumbAppendLog(options, new File(cacheDir, BREADCRUMBS_FILENAME));
          });

  // Latest pending value per file (or DELETE_MARKER), coalesced until the next flush.
  private final @NotNull Map<String, Object> pendingWrites = new ConcurrentHashMap<>();
  // Breadcrumbs buffered since the last flush, appended together behind a single fsync.
  private final @NotNull Queue<Breadcrumb> pendingBreadcrumbs = new ConcurrentLinkedQueue<>();
  private final @NotNull AtomicBoolean pendingBreadcrumbsClear = new AtomicBoolean(false);
  private final @NotNull AtomicBoolean hasPendingFlush = new AtomicBoolean(false);

  public PersistingScopeObserver(final @NotNull SentryOptions options) {
    this.options = options;
  }

  @Override
  public void setUser(final @Nullable User user) {
    enqueue(USER_FILENAME, user);
  }

  @Override
  public void addBreadcrumb(@NotNull Breadcrumb crumb) {
    if (!options.isEnableScopePersistence()) {
      return;
    }
    pendingBreadcrumbs.offer(crumb);
    requestFlush();
  }

  @Override
  public void setBreadcrumbs(@NotNull Collection<Breadcrumb> breadcrumbs) {
    if (breadcrumbs.isEmpty()) {
      // we only clear the queue if the new collection is empty (someone called clearBreadcrumbs)
      // If it's not empty, we'd add breadcrumbs one-by-one in the method above
      if (!options.isEnableScopePersistence()) {
        return;
      }
      // drop breadcrumbs buffered before the clear; anything added after it is enqueued again
      pendingBreadcrumbs.clear();
      pendingBreadcrumbsClear.set(true);
      requestFlush();
    }
  }

  @Override
  public void setTags(@NotNull Map<String, @NotNull String> tags) {
    enqueue(TAGS_FILENAME, tags);
  }

  @Override
  public void setExtras(@NotNull Map<String, @NotNull Object> extras) {
    enqueue(EXTRAS_FILENAME, extras);
  }

  @Override
  public void setRequest(@Nullable Request request) {
    enqueue(REQUEST_FILENAME, request);
  }

  @Override
  public void setFingerprint(@NotNull Collection<String> fingerprint) {
    enqueue(FINGERPRINT_FILENAME, fingerprint);
  }

  @Override
  public void setLevel(@Nullable SentryLevel level) {
    enqueue(LEVEL_FILENAME, level);
  }

  @Override
  public void setTransaction(@Nullable String transaction) {
    enqueue(TRANSACTION_FILENAME, transaction);
  }

  @Override
  public void setTrace(@Nullable SpanContext spanContext, @NotNull IScope scope) {
    // we always need a trace_id to properly link with traces/replays, so we fallback to
    // propagation context values and create a fake SpanContext
    enqueue(
        TRACE_FILENAME,
        spanContext == null ? scope.getPropagationContext().toSpanContext() : spanContext);
  }

  @Override
  public void setContexts(@NotNull Contexts contexts) {
    enqueue(CONTEXTS_FILENAME, contexts);
  }

  @Override
  public void setReplayId(@NotNull SentryId replayId) {
    enqueue(REPLAY_FILENAME, replayId);
  }

  private void enqueue(final @NotNull String fileName, final @Nullable Object entity) {
    if (!options.isEnableScopePersistence()) {
      return;
    }
    // latest value wins; a null entity means the file should be deleted on the next flush
    pendingWrites.put(fileName, entity == null ? DELETE_MARKER : entity);
    requestFlush();
  }

  /**
   * Queues a flush unless one is already queued. Coalescing comes from the flush task sitting in
   * the executor queue: every mutation that arrives before it runs is folded into the same write.
   * The executor is single-threaded, so during startup — when mutations are frequent and the queue
   * is deep — that window covers many mutations.
   */
  private void requestFlush() {
    if (!hasPendingFlush.compareAndSet(false, true)) {
      // a flush is already queued; it will pick up the latest pending state
      return;
    }
    try {
      final @NotNull Future<?> future = options.getExecutorService().submit(this::flushOnExecutor);
      if (future.isCancelled()) {
        // the executor rejects tasks without throwing once its queue is full, so clear the flag or
        // no later mutation would ever be able to queue a flush again
        hasPendingFlush.set(false);
      }
    } catch (Throwable e) {
      hasPendingFlush.set(false);
      options.getLogger().log(ERROR, "Scope persistence flush could not be submitted", e);
    }
  }

  private void flushOnExecutor() {
    runSafely(this::flushPending);
    // clear the flag before re-checking, otherwise a mutation landing between the drain and the
    // clear would see a flush still queued and be left with nobody to write it
    hasPendingFlush.set(false);
    if (!pendingWrites.isEmpty()
        || !pendingBreadcrumbs.isEmpty()
        || pendingBreadcrumbsClear.get()) {
      requestFlush();
    }
  }

  /** Writes all coalesced scope state to disk. Does I/O; must run off the caller/main thread. */
  private void flushPending() {
    for (final @NotNull String fileName : new ArrayList<>(pendingWrites.keySet())) {
      final @Nullable Object entity = pendingWrites.remove(fileName);
      if (entity == null) {
        // removed by a concurrent flush
        continue;
      }
      if (entity == DELETE_MARKER) {
        delete(fileName);
      } else {
        store(entity, fileName);
      }
    }

    final @NotNull BreadcrumbAppendLog log = breadcrumbsLog.getValue();

    if (pendingBreadcrumbsClear.compareAndSet(true, false)) {
      log.clear();
    }

    // drain into a list first so the whole batch goes out in a single append
    final @NotNull List<Breadcrumb> drained = new ArrayList<>();
    Breadcrumb crumb;
    while ((crumb = pendingBreadcrumbs.poll()) != null) {
      drained.add(crumb);
    }
    log.append(drained);
  }

  /**
   * Synchronously writes any pending scope state to disk. Does I/O on the calling thread, so it's
   * only meant for tests and shutdown, not the hot path.
   */
  @TestOnly
  void flush() {
    runSafely(this::flushPending);
  }

  private void runSafely(final @NotNull Runnable task) {
    try {
      task.run();
    } catch (Throwable e) {
      options.getLogger().log(ERROR, "Serialization task failed", e);
    }
  }

  private <T> void store(final @NotNull T entity, final @NotNull String fileName) {
    store(options, entity, fileName);
  }

  private void delete(final @NotNull String fileName) {
    CacheUtils.delete(options, SCOPE_CACHE, fileName);
  }

  public static <T> void store(
      final @NotNull SentryOptions options,
      final @NotNull T entity,
      final @NotNull String fileName) {
    CacheUtils.store(options, entity, SCOPE_CACHE, fileName);
  }

  public <T> @Nullable T read(
      final @NotNull SentryOptions options,
      final @NotNull String fileName,
      final @NotNull Class<T> clazz) {
    if (fileName.equals(BREADCRUMBS_FILENAME)) {
      return clazz.cast(breadcrumbsLog.getValue().read());
    }
    return CacheUtils.read(options, SCOPE_CACHE, fileName, clazz, null);
  }

  /**
   * Resets the scope cache by deleting the persisted files. Note: this does I/O and should be
   * called from a background thread.
   */
  public void resetCache() {
    // NOTE: pending mutations are deliberately left alone. They only ever hold values from the
    // current process, which is exactly what this reset is clearing the way for; dropping them
    // would lose scope state set during init.
    breadcrumbsLog.getValue().clear();

    // the rest we can safely delete
    delete(USER_FILENAME);
    delete(LEVEL_FILENAME);
    delete(REQUEST_FILENAME);
    delete(FINGERPRINT_FILENAME);
    delete(CONTEXTS_FILENAME);
    delete(EXTRAS_FILENAME);
    delete(TAGS_FILENAME);
    delete(TRACE_FILENAME);
    delete(TRANSACTION_FILENAME);
  }
}
