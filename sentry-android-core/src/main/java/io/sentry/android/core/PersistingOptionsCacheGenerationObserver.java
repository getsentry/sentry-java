package io.sentry.android.core;

import static io.sentry.cache.PersistingOptionsObserver.OPTIONS_CACHE;

import io.sentry.IOptionsObserver;
import io.sentry.SentryOptions;
import io.sentry.protocol.SdkVersion;
import io.sentry.util.FileUtils;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class PersistingOptionsCacheGenerationObserver implements IOptionsObserver {
  static final String APP_LAST_UPDATE_TIME_FILENAME = "app-last-update-time.json";

  private static final Charset UTF_8 = Charset.forName("UTF-8");

  private final @NotNull SentryOptions options;
  private final long lastUpdateTime;

  PersistingOptionsCacheGenerationObserver(
      final @NotNull SentryOptions options, final long lastUpdateTime) {
    this.options = options;
    this.lastUpdateTime = lastUpdateTime;
  }

  @Override
  public void setRelease(final @Nullable String release) {
    final File cacheDir = new File(options.getCacheDirPath(), OPTIONS_CACHE);
    cacheDir.mkdirs();
    try (final OutputStream stream =
        new FileOutputStream(new File(cacheDir, APP_LAST_UPDATE_TIME_FILENAME))) {
      stream.write(Long.toString(lastUpdateTime).getBytes(UTF_8));
    } catch (Throwable e) {
      options
          .getLogger()
          .log(io.sentry.SentryLevel.ERROR, e, "Failed to persist options cache generation.");
    }
  }

  static @Nullable Long read(final @NotNull SentryOptions options) {
    if (options.getCacheDirPath() == null) {
      return null;
    }
    try {
      final String value =
          FileUtils.readText(
              new File(
                  new File(options.getCacheDirPath(), OPTIONS_CACHE),
                  APP_LAST_UPDATE_TIME_FILENAME));
      return value == null ? null : Long.valueOf(value);
    } catch (Throwable e) {
      options
          .getLogger()
          .log(io.sentry.SentryLevel.ERROR, e, "Failed to read options cache generation.");
      return null;
    }
  }

  @Override
  public void setProguardUuid(final @Nullable String proguardUuid) {}

  @Override
  public void setSdkVersion(final @Nullable SdkVersion sdkVersion) {}

  @Override
  public void setEnvironment(final @Nullable String environment) {}

  @Override
  public void setDist(final @Nullable String dist) {}

  @Override
  public void setTags(final @NotNull Map<String, @NotNull String> tags) {}

  @Override
  public void setReplayErrorSampleRate(final @Nullable Double replayErrorSampleRate) {}
}
