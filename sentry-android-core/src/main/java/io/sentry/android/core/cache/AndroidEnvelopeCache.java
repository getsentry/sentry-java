package io.sentry.android.core.cache;

import io.sentry.Hint;
import io.sentry.SentryEnvelope;
import io.sentry.SentryOptions;
import io.sentry.android.core.AppStartState;
import io.sentry.android.core.SentryAndroidOptions;
import io.sentry.cache.EnvelopeCache;
import io.sentry.hints.DiskFlushNotification;
import io.sentry.util.HintUtils;
import io.sentry.util.Objects;
import java.io.File;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import static io.sentry.SentryLevel.DEBUG;
import static io.sentry.SentryLevel.ERROR;

@ApiStatus.Internal
public final class AndroidEnvelopeCache extends EnvelopeCache {

  public static final String STARTUP_CRASH_MARKER_FILE = "startup_crash";

  private final @NotNull SentryAndroidOptions options;

  public AndroidEnvelopeCache(
    final @NotNull SentryAndroidOptions options
  ) {
    super(options, Objects.requireNonNull(options.getCacheDirPath(), "cacheDirPath must not be null"), options.getMaxCacheItems());
    this.options = Objects.requireNonNull(options, "SentryAndroidOptions is required.");
  }

  @Override public void store(@NotNull SentryEnvelope envelope, @NotNull Hint hint) {
    super.store(envelope, hint);

    final Long appStartTime = AppStartState.getInstance().getAppStartMillis();
    if (HintUtils.hasType(hint, DiskFlushNotification.class) && appStartTime != null) {
      long timeSinceSdkInit = System.currentTimeMillis() - appStartTime;
      if (timeSinceSdkInit <= options.getStartupCrashDurationThresholdMillis()) {
        options
          .getLogger()
          .log(DEBUG,
            "Startup Crash detected %d milliseconds after SDK init. Writing a startup crash marker file to disk.",
            timeSinceSdkInit);
        writeStartupCrashMarkerFile();
      }
    }
  }

  private void writeStartupCrashMarkerFile() {
    final File crashMarkerFile = new File(options.getCacheDirPath(), STARTUP_CRASH_MARKER_FILE);
    try {
      crashMarkerFile.createNewFile();
    } catch (Throwable e) {
      options.getLogger().log(ERROR, "Error writing the startup crash marker file to the disk", e);
    }
  }

  public static boolean hasStartupCrashMarker(final @NotNull String dirPath,
    final @NotNull SentryOptions options) {
    final File crashMarkerFile = new File(dirPath, STARTUP_CRASH_MARKER_FILE);
    try {
      final boolean exists = crashMarkerFile.exists();
      if (exists) {
        if (!crashMarkerFile.delete()) {
          options
            .getLogger()
            .log(
              ERROR,
              "Failed to delete the startup crash marker file. %s.",
              crashMarkerFile.getAbsolutePath());
        }
      }
      return exists;
    } catch (Throwable e) {
      options.getLogger().log(ERROR, "Error reading/deleting the startup crash marker file on the disk", e);
    }
    return false;
  }
}
