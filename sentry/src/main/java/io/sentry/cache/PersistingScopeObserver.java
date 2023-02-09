package io.sentry.cache;

import io.sentry.Breadcrumb;
import io.sentry.IScopeObserver;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.SpanContext;
import io.sentry.protocol.Contexts;
import io.sentry.protocol.Request;
import io.sentry.protocol.SdkVersion;
import io.sentry.protocol.User;
import io.sentry.util.Objects;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static io.sentry.SentryLevel.DEBUG;
import static io.sentry.SentryLevel.ERROR;

public class PersistingScopeObserver implements IScopeObserver {

  @SuppressWarnings("CharsetObjectCanBeUsed")
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
  public static final String RELEASE_FILENAME = "release.json";
  public static final String PROGUARD_UUID_FILENAME = "proguard-uuid.json";
  public static final String SDK_VERSION_FILENAME = "sdk-version.json";
  public static final String ENVIRONMENT_FILENAME = "environment.json";
  public static final String DIST_FILENAME = "dist.json";

  private final @NotNull SentryOptions options;
  private final @NotNull File scopeCacheDir;

  public PersistingScopeObserver(final @NotNull SentryOptions options) {
    this.options = options;
    final String cacheDir =
      Objects.requireNonNull(options.getCacheDirPath(), "cacheDirPath must not be null");
    scopeCacheDir = new File(cacheDir, SCOPE_CACHE);
  }

  @Override public void setUser(final @Nullable User user) {
    serializeToDisk(() -> {
      if (user == null) {
        delete(USER_FILENAME);
      } else {
        store(user, USER_FILENAME);
      }
    });
  }

  @Override public void setBreadcrumbs(@NotNull Collection<Breadcrumb> breadcrumbs) {
    serializeToDisk(() -> store(breadcrumbs, BREADCRUMBS_FILENAME));
  }

  @Override public void setTags(@NotNull Map<String, @NotNull String> tags) {
    serializeToDisk(() -> store(tags, TAGS_FILENAME));
  }

  @Override public void setExtras(@NotNull Map<String, @NotNull Object> extras) {
    serializeToDisk(() -> store(extras, EXTRAS_FILENAME));
  }

  @Override public void setRequest(@Nullable Request request) {
    serializeToDisk(() -> {
      if (request == null) {
        delete(REQUEST_FILENAME);
      } else {
        store(request, REQUEST_FILENAME);
      }
    });
  }

  @Override public void setFingerprint(@NotNull Collection<String> fingerprint) {
    serializeToDisk(() -> store(fingerprint, FINGERPRINT_FILENAME));
  }

  @Override public void setLevel(@Nullable SentryLevel level) {
    serializeToDisk(() -> {
      if (level == null) {
        delete(LEVEL_FILENAME);
      } else {
        store(level, LEVEL_FILENAME);
      }
    });
  }

  @Override public void setTransaction(@Nullable String transaction) {
    serializeToDisk(() -> {
      if (transaction == null) {
        delete(TRANSACTION_FILENAME);
      } else {
        store(transaction, TRANSACTION_FILENAME);
      }
    });
  }

  @Override public void setTrace(@Nullable SpanContext spanContext) {
    serializeToDisk(() -> {
      if (spanContext == null) {
        delete(TRACE_FILENAME);
      } else {
        store(spanContext, TRACE_FILENAME);
      }
    });
  }

  @Override public void setContexts(@NotNull Contexts contexts) {
    serializeToDisk(() -> store(contexts, CONTEXTS_FILENAME));
  }

  @Override public void setRelease(@Nullable String release) {
    serializeToDisk(() -> {
      if (release == null) {
        delete(RELEASE_FILENAME);
      } else {
        store(release, RELEASE_FILENAME);
      }
    });
  }

  @Override public void setProguardUuid(@Nullable String proguardUuid) {
    serializeToDisk(() -> {
      if (proguardUuid == null) {
        delete(PROGUARD_UUID_FILENAME);
      } else {
        store(proguardUuid, PROGUARD_UUID_FILENAME);
      }
    });
  }

  @Override public void setSdkVersion(@Nullable SdkVersion sdkVersion) {
    serializeToDisk(() -> {
      if (sdkVersion == null) {
        delete(SDK_VERSION_FILENAME);
      } else {
        store(sdkVersion, SDK_VERSION_FILENAME);
      }
    });
  }

  @Override public void setDist(@Nullable String dist) {
    serializeToDisk(() -> {
      if (dist == null) {
        delete(DIST_FILENAME);
      } else {
        store(dist, DIST_FILENAME);
      }
    });
  }

  @Override public void setEnvironment(@Nullable String environment) {
    serializeToDisk(() -> {
      if (environment == null) {
        delete(ENVIRONMENT_FILENAME);
      } else {
        store(environment, ENVIRONMENT_FILENAME);
      }
    });
  }

  private void serializeToDisk(final @NotNull Runnable task) {
    try {
      options.getExecutorService().submit(() -> {
        try {
          task.run();
        } catch (Throwable e) {
          options
            .getLogger()
            .log(ERROR, "Serialization task failed", e);
        }
      });
    } catch (RejectedExecutionException e) {
      options
        .getLogger()
        .log(ERROR, "Serialization task got rejected", e);
    }
  }

  private <T> void store(final @NotNull T entity, final @NotNull String fileName) {
    scopeCacheDir.mkdirs();

    final File file = new File(scopeCacheDir, fileName);
    if (file.exists()) {
      options.getLogger().log(DEBUG, "Overwriting %s in scope cache", fileName);
      if (!file.delete()) {
        options.getLogger().log(SentryLevel.ERROR, "Failed to delete: %s", file.getAbsolutePath());
      }
    }

    try (final OutputStream outputStream = new FileOutputStream(file);
         final Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream, UTF_8))) {
      options.getSerializer().serialize(entity, writer);
    } catch (Throwable e) {
      options.getLogger().log(ERROR, e, "Error persisting entity: %s", fileName);
    }
  }

  private void delete(final @NotNull String fileName) {
    scopeCacheDir.mkdirs();

    final File file = new File(scopeCacheDir, fileName);
    if (file.exists()) {
      options.getLogger().log(DEBUG, "Deleting %s from scope cache", fileName);
      if (!file.delete()) {
        options.getLogger().log(SentryLevel.ERROR, "Failed to delete: %s", file.getAbsolutePath());
      }
    }
  }

  private static File ensureScopeCache() {

  }

  public static <T> @Nullable T read(
    final @NotNull SentryOptions options,
    final @NotNull String fileName,
    final @NotNull Class<T> clazz
  ) {
    final File file = new File(scopeCacheDir, fileName);
    if (file.exists()) {
      try (final Reader reader = new BufferedReader(
        new InputStreamReader(new FileInputStream(file), UTF_8)
      )) {
        return options.getSerializer().deserialize(reader, clazz);
      } catch (Throwable e) {
        options.getLogger().log(ERROR, e, "Error reading entity: %s", fileName);
      }
    } else {
      options.getLogger().log(DEBUG, "No entry stored for %s", fileName);
    }
    return null;
  }
}
