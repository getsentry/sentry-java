package io.sentry.cache;

import static io.sentry.SentryLevel.ERROR;

import io.sentry.Breadcrumb;
import io.sentry.IScope;
import io.sentry.JsonDeserializer;
import io.sentry.ScopeObserverAdapter;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.SpanContext;
import io.sentry.protocol.Contexts;
import io.sentry.protocol.Request;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.User;
import java.util.Collection;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PersistingScopeObserver extends ScopeObserverAdapter {

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

  private final @NotNull SentryOptions options;

  public PersistingScopeObserver(final @NotNull SentryOptions options) {
    this.options = options;
  }

  @Override
  public void setUser(final @Nullable User user) {
    serializeToDisk(
        () -> {
          if (user == null) {
            delete(USER_FILENAME);
          } else {
            store(user, USER_FILENAME);
          }
        });
  }

  @Override
  public void setBreadcrumbs(@NotNull Collection<Breadcrumb> breadcrumbs) {
    serializeToDisk(() -> store(breadcrumbs, BREADCRUMBS_FILENAME));
  }

  @Override
  public void setTags(@NotNull Map<String, @NotNull String> tags) {
    serializeToDisk(() -> store(tags, TAGS_FILENAME));
  }

  @Override
  public void setExtras(@NotNull Map<String, @NotNull Object> extras) {
    serializeToDisk(() -> store(extras, EXTRAS_FILENAME));
  }

  @Override
  public void setRequest(@Nullable Request request) {
    serializeToDisk(
        () -> {
          if (request == null) {
            delete(REQUEST_FILENAME);
          } else {
            store(request, REQUEST_FILENAME);
          }
        });
  }

  @Override
  public void setFingerprint(@NotNull Collection<String> fingerprint) {
    serializeToDisk(() -> store(fingerprint, FINGERPRINT_FILENAME));
  }

  @Override
  public void setLevel(@Nullable SentryLevel level) {
    serializeToDisk(
        () -> {
          if (level == null) {
            delete(LEVEL_FILENAME);
          } else {
            store(level, LEVEL_FILENAME);
          }
        });
  }

  @Override
  public void setTransaction(@Nullable String transaction) {
    serializeToDisk(
        () -> {
          if (transaction == null) {
            delete(TRANSACTION_FILENAME);
          } else {
            store(transaction, TRANSACTION_FILENAME);
          }
        });
  }

  @Override
  public void setTrace(@Nullable SpanContext spanContext, @NotNull IScope scope) {
    serializeToDisk(
        () -> {
          if (spanContext == null) {
            // we always need a trace_id to properly link with traces/replays, so we fallback to
            // propagation context values and create a fake SpanContext
            store(scope.getPropagationContext().toSpanContext(), TRACE_FILENAME);
          } else {
            store(spanContext, TRACE_FILENAME);
          }
        });
  }

  @Override
  public void setContexts(@NotNull Contexts contexts) {
    serializeToDisk(() -> store(contexts, CONTEXTS_FILENAME));
  }

  @Override
  public void setReplayId(@NotNull SentryId replayId) {
    serializeToDisk(() -> store(replayId, REPLAY_FILENAME));
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  private void serializeToDisk(final @NotNull Runnable task) {
    if (Thread.currentThread().getName().contains("SentryExecutor")) {
      // we're already on the sentry executor thread, so we can just execute it directly
      task.run();
      return;
    }

    try {
      options
          .getExecutorService()
          .submit(
              () -> {
                try {
                  task.run();
                } catch (Throwable e) {
                  options.getLogger().log(ERROR, "Serialization task failed", e);
                }
              });
    } catch (Throwable e) {
      options.getLogger().log(ERROR, "Serialization task could not be scheduled", e);
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

  public static <T> @Nullable T read(
      final @NotNull SentryOptions options,
      final @NotNull String fileName,
      final @NotNull Class<T> clazz) {
    return read(options, fileName, clazz, null);
  }

  public static <T, R> @Nullable T read(
      final @NotNull SentryOptions options,
      final @NotNull String fileName,
      final @NotNull Class<T> clazz,
      final @Nullable JsonDeserializer<R> elementDeserializer) {
    return CacheUtils.read(options, SCOPE_CACHE, fileName, clazz, elementDeserializer);
  }
}
