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
import java.util.Collection;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PersistingScopeObserver extends ScopeObserverAdapter {

  private static final Charset UTF_8 = Charset.forName("UTF-8");

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
  private final @NotNull LazyEvaluator<ObjectQueue<Breadcrumb>> breadcrumbsQueue = new LazyEvaluator<>(() -> {
    final File cacheDir = ensureCacheDir(options, SCOPE_CACHE);
    if (cacheDir == null) {
      options.getLogger().log(INFO, "Cache dir is not set, cannot store in scope cache");
      return ObjectQueue.createEmpty();
    }

    QueueFile queueFile = null;
    final File file = new File(cacheDir, BREADCRUMBS_FILENAME);
    try {
      try {
        queueFile = new QueueFile.Builder(file)
          .size(options.getMaxBreadcrumbs())
          .build();
      } catch (IOException e) {
        // if file is corrupted we simply delete it and try to create it again. We accept the trade
        // off of losing breadcrumbs for ANRs that happened right before the app has received an
        // update where the new format was introduced
        file.delete();

        queueFile = new QueueFile.Builder(file)
          .size(options.getMaxBreadcrumbs())
          .build();
      }
    } catch (IOException e) {
      options.getLogger().log(ERROR, "Failed to create breadcrumbs queue", e);
      return ObjectQueue.createEmpty();
    }
    return ObjectQueue.create(queueFile,
      new ObjectQueue.Converter<Breadcrumb>() {
        @Override
        @Nullable
        public Breadcrumb from(byte[] source) {
          try (final Reader reader =
                 new BufferedReader(new InputStreamReader(new ByteArrayInputStream(source), UTF_8))) {
            return options.getSerializer().deserialize(reader, Breadcrumb.class);
          } catch (Throwable e) {
            options.getLogger().log(ERROR, e, "Error reading entity from scope cache");
          }
          return null;
        }

        @Override public void toStream(Breadcrumb value, OutputStream sink) throws IOException {
          try (final Writer writer = new BufferedWriter(new OutputStreamWriter(sink, UTF_8))) {
            options.getSerializer().serialize(value, writer);
          }
        }
      });
  });

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

  @Override public void addBreadcrumb(@NotNull Breadcrumb crumb) {
    serializeToDisk(() -> {
      try {
        breadcrumbsQueue.getValue().add(crumb);
      } catch (IOException e) {
        options.getLogger().log(ERROR, "Failed to add breadcrumb to file queue", e);
      }
    });
  }

  @Override
  public void setBreadcrumbs(@NotNull Collection<Breadcrumb> breadcrumbs) {
    if (breadcrumbs.isEmpty()) {
      serializeToDisk(() -> {
        try {
          breadcrumbsQueue.getValue().clear();
        } catch (IOException e) {
          options.getLogger().log(ERROR, "Failed to clear breadcrumbs from file queue", e);
        }
      });
    }
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
    if (!options.isEnableScopePersistence()) {
      return;
    }
    if (Thread.currentThread().getName().contains("SentryExecutor")) {
      // we're already on the sentry executor thread, so we can just execute it directly
      try {
        task.run();
      } catch (Throwable e) {
        options.getLogger().log(ERROR, "Serialization task failed", e);
      }
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
}
