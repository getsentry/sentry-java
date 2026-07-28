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
import io.sentry.cache.tape.ObjectQueue;
import io.sentry.cache.tape.QueueFile;
import io.sentry.protocol.Contexts;
import io.sentry.protocol.Request;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.User;
import io.sentry.util.LazyEvaluator;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PersistingScopeObserver extends ScopeObserverAdapter {

  private static final Charset UTF_8 = Charset.forName("UTF-8");

  /** Sentinel value marking a file that should be deleted rather than written on the next flush. */
  private static final Object DELETE_MARKER = new Object();

  /** Sentinel value marking a breadcrumb clear in the pending breadcrumb operations. */
  private static final Object CLEAR_MARKER = new Object();

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
  private final @NotNull LazyEvaluator<ObjectQueue<Breadcrumb>> breadcrumbsQueue =
      new LazyEvaluator<>(
          () -> {
            final File cacheDir = ensureCacheDir(options, SCOPE_CACHE);
            if (cacheDir == null) {
              options.getLogger().log(INFO, "Cache dir is not set, cannot store in scope cache");
              return ObjectQueue.createEmpty();
            }

            QueueFile queueFile = null;
            final File file = new File(cacheDir, BREADCRUMBS_FILENAME);
            try {
              try {
                queueFile =
                    new QueueFile.Builder(file)
                        .size(options.getMaxBreadcrumbs())
                        .synchronousWrites(false)
                        .build();
              } catch (IOException e) {
                // if file is corrupted we simply delete it and try to create it again. We accept
                // the trade
                // off of losing breadcrumbs for ANRs that happened right before the app has
                // received an
                // update where the new format was introduced
                file.delete();

                queueFile =
                    new QueueFile.Builder(file)
                        .size(options.getMaxBreadcrumbs())
                        .synchronousWrites(false)
                        .build();
              }
            } catch (IOException e) {
              options.getLogger().log(ERROR, "Failed to create breadcrumbs queue", e);
              return ObjectQueue.createEmpty();
            }
            return ObjectQueue.create(
                queueFile,
                new ObjectQueue.Converter<Breadcrumb>() {
                  @Override
                  @Nullable
                  public Breadcrumb from(byte[] source) {
                    try (final Reader reader =
                        new BufferedReader(
                            new InputStreamReader(new ByteArrayInputStream(source), UTF_8))) {
                      return options.getSerializer().deserialize(reader, Breadcrumb.class);
                    } catch (Throwable e) {
                      options.getLogger().log(ERROR, e, "Error reading entity from scope cache");
                    }
                    return null;
                  }

                  @Override
                  public void toStream(Breadcrumb value, OutputStream sink) throws IOException {
                    try (final Writer writer =
                        new BufferedWriter(new OutputStreamWriter(sink, UTF_8))) {
                      options.getSerializer().serialize(value, writer);
                    }
                  }
                });
          });

  // Latest pending value per file (or DELETE_MARKER), coalesced until the next flush.
  private final @NotNull Map<String, Object> pendingWrites = new ConcurrentHashMap<>();
  // Tracks requests to add and clear breadcrumb since the last flush, applied together behind a
  // single fsync. Adds and clears share one queue so their relative order survives batching:
  // a clear must not wipe a breadcrumb added after it.
  private final @NotNull Queue<Object> pendingBreadcrumbs = new ConcurrentLinkedQueue<>();
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
      pendingBreadcrumbs.offer(CLEAR_MARKER);
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
      final @NotNull Future<?> future = options.getExecutorService().submit(this::flush);
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

  /**
   * Writes all coalesced scope state to disk. Runs as the queued flush task on the Sentry executor,
   * which is also the only place it may run: it does I/O, and it owns clearing {@link
   * #hasPendingFlush} once the write is done.
   */
  private void flush() {
    try {
      runSafely(this::writePending);
    } finally {
      // clear the flag before re-checking, otherwise a mutation landing between the drain and the
      // clear would see a flush still queued and be left with nobody to write it. In a finally so
      // an unexpected throw can't leave the flag set and stop persistence for the whole process.
      hasPendingFlush.set(false);
      if (!pendingWrites.isEmpty() || !pendingBreadcrumbs.isEmpty()) {
        requestFlush();
      }
    }
  }

  private void writePending() {
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

    boolean breadcrumbsChanged = false;
    final @NotNull ObjectQueue<Breadcrumb> queue = breadcrumbsQueue.getValue();

    Object operation;
    while ((operation = pendingBreadcrumbs.poll()) != null) {
      try {
        if (operation == CLEAR_MARKER) {
          queue.clear();
        } else {
          queue.add((Breadcrumb) operation);
        }
        breadcrumbsChanged = true;
      } catch (IOException e) {
        options.getLogger().log(ERROR, "Failed to apply breadcrumb change to file queue", e);
      }
    }

    if (breadcrumbsChanged) {
      try {
        // single fsync for the whole batch instead of one per breadcrumb
        queue.sync();
      } catch (IOException e) {
        options.getLogger().log(ERROR, "Failed to sync breadcrumbs file queue", e);
      }
    }
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
      try {
        return clazz.cast(breadcrumbsQueue.getValue().asList());
      } catch (IOException e) {
        options.getLogger().log(ERROR, "Unable to read serialized breadcrumbs from QueueFile");
        return null;
      }
    }
    return CacheUtils.read(options, SCOPE_CACHE, fileName, clazz, null);
  }

  /**
   * Resets the scope cache by deleting the files and/or clearing the QueueFiles. Note: this does
   * I/O and should be called from a background thread.
   */
  public void resetCache() {
    // NOTE: pending mutations are deliberately left alone. They only ever hold values from the
    // current process, which is exactly what this reset is clearing the way for; dropping them
    // would lose scope state set during init.
    // since it keeps a reference to the file and we cannot delete it, breadcrumbs we just clear
    try {
      final @NotNull ObjectQueue<Breadcrumb> queue = breadcrumbsQueue.getValue();
      queue.clear();
      // breadcrumbs use buffered writes, so make the clear durable explicitly
      queue.sync();
    } catch (IOException e) {
      options.getLogger().log(ERROR, "Failed to clear breadcrumbs from file queue", e);
    }

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
