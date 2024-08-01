package io.sentry.cache;

import io.sentry.IOptionsObserver;
import io.sentry.JsonDeserializer;
import io.sentry.SentryOptions;
import io.sentry.protocol.SdkVersion;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PersistingOptionsObserver implements IOptionsObserver {
  public static final String OPTIONS_CACHE = ".options-cache";
  public static final String RELEASE_FILENAME = "release.json";
  public static final String PROGUARD_UUID_FILENAME = "proguard-uuid.json";
  public static final String SDK_VERSION_FILENAME = "sdk-version.json";
  public static final String ENVIRONMENT_FILENAME = "environment.json";
  public static final String DIST_FILENAME = "dist.json";
  public static final String TAGS_FILENAME = "tags.json";
  public static final String REPLAY_ERROR_SAMPLE_RATE_FILENAME = "replay-error-sample-rate.json";

  private final @NotNull SentryOptions options;

  public PersistingOptionsObserver(final @NotNull SentryOptions options) {
    this.options = options;
  }

  @Override
  public void setRelease(@Nullable String release) {
    if (release == null) {
      delete(RELEASE_FILENAME);
    } else {
      store(release, RELEASE_FILENAME);
    }
  }

  @Override
  public void setProguardUuid(@Nullable String proguardUuid) {
    if (proguardUuid == null) {
      delete(PROGUARD_UUID_FILENAME);
    } else {
      store(proguardUuid, PROGUARD_UUID_FILENAME);
    }
  }

  @Override
  public void setSdkVersion(@Nullable SdkVersion sdkVersion) {
    if (sdkVersion == null) {
      delete(SDK_VERSION_FILENAME);
    } else {
      store(sdkVersion, SDK_VERSION_FILENAME);
    }
  }

  @Override
  public void setDist(@Nullable String dist) {
    if (dist == null) {
      delete(DIST_FILENAME);
    } else {
      store(dist, DIST_FILENAME);
    }
  }

  @Override
  public void setEnvironment(@Nullable String environment) {
    if (environment == null) {
      delete(ENVIRONMENT_FILENAME);
    } else {
      store(environment, ENVIRONMENT_FILENAME);
    }
  }

  @Override
  public void setTags(@NotNull Map<String, @NotNull String> tags) {
    store(tags, TAGS_FILENAME);
  }

  @Override
  public void setReplayErrorSampleRate(@Nullable Double replayErrorSampleRate) {
    if (replayErrorSampleRate == null) {
      delete(REPLAY_ERROR_SAMPLE_RATE_FILENAME);
    } else {
      store(replayErrorSampleRate.toString(), REPLAY_ERROR_SAMPLE_RATE_FILENAME);
    }
  }

  private <T> void store(final @NotNull T entity, final @NotNull String fileName) {
    CacheUtils.store(options, entity, OPTIONS_CACHE, fileName);
  }

  private void delete(final @NotNull String fileName) {
    CacheUtils.delete(options, OPTIONS_CACHE, fileName);
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
    return CacheUtils.read(options, OPTIONS_CACHE, fileName, clazz, elementDeserializer);
  }
}
