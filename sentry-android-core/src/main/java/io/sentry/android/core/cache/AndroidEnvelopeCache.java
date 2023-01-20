package io.sentry.android.core.cache;

import static io.sentry.SentryLevel.DEBUG;
import static io.sentry.SentryLevel.ERROR;

import io.sentry.Hint;
import io.sentry.SentryEnvelope;
import io.sentry.SentryOptions;
import io.sentry.android.core.AppStartState;
import io.sentry.android.core.SentryAndroidOptions;
import io.sentry.android.core.internal.util.AndroidCurrentDateProvider;
import io.sentry.cache.EnvelopeCache;
import io.sentry.hints.DiskFlushNotification;
import io.sentry.transport.ICurrentDateProvider;
import io.sentry.util.HintUtils;
import io.sentry.util.Objects;
import java.io.File;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

@ApiStatus.Internal
public final class AndroidEnvelopeCache extends EnvelopeCache {

  private final @NotNull ICurrentDateProvider currentDateProvider;

  public AndroidEnvelopeCache(final @NotNull SentryAndroidOptions options) {
    this(options, AndroidCurrentDateProvider.getInstance());
  }

  AndroidEnvelopeCache(
      final @NotNull SentryAndroidOptions options,
      final @NotNull ICurrentDateProvider currentDateProvider) {
    super(
        options,
        Objects.requireNonNull(options.getCacheDirPath(), "cacheDirPath must not be null"),
        options.getMaxCacheItems());
    this.currentDateProvider = currentDateProvider;
  }

  @Override
  public void store(@NotNull SentryEnvelope envelope, @NotNull Hint hint) {
    super.store(envelope, hint);

    final SentryAndroidOptions options = (SentryAndroidOptions) this.options;

    final Long appStartTime = AppStartState.getInstance().getAppStartMillis();
    if (HintUtils.hasType(hint, DiskFlushNotification.class) && appStartTime != null) {
      long timeSinceSdkInit = currentDateProvider.getCurrentTimeMillis() - appStartTime;
      if (timeSinceSdkInit <= options.getStartupCrashDurationThresholdMillis()) {
        options
            .getLogger()
            .log(
                DEBUG,
                "Startup Crash detected %d milliseconds after SDK init. Writing a startup crash marker file to disk.",
                timeSinceSdkInit);
        writeStartupCrashMarkerFile();
      }
    }
  }

  @TestOnly
  public @NotNull File getDirectory() {
    return directory;
  }

  private void writeStartupCrashMarkerFile() {
    // we use outbox path always, as it's the one that will also contain markers if hybrid sdks
    // decide to write it, which will trigger the blocking init
    final String outboxPath = options.getOutboxPath();
    if (outboxPath == null) {
      options
          .getLogger()
          .log(DEBUG, "Outbox path is null, the startup crash marker file will not be written");
      return;
    }
    final File crashMarkerFile = new File(options.getOutboxPath(), STARTUP_CRASH_MARKER_FILE);
    try {
      crashMarkerFile.createNewFile();
    } catch (Throwable e) {
      options.getLogger().log(ERROR, "Error writing the startup crash marker file to the disk", e);
    }
  }

  public static boolean hasStartupCrashMarker(final @NotNull SentryOptions options) {
    final String outboxPath = options.getOutboxPath();
    if (outboxPath == null) {
      options
          .getLogger()
          .log(DEBUG, "Outbox path is null, the startup crash marker file does not exist");
      return false;
    }

    final File crashMarkerFile = new File(options.getOutboxPath(), STARTUP_CRASH_MARKER_FILE);
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
      options
          .getLogger()
          .log(ERROR, "Error reading/deleting the startup crash marker file on the disk", e);
    }
    return false;
  }
}
