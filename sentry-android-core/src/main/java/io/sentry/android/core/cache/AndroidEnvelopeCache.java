package io.sentry.android.core.cache;

import static io.sentry.SentryLevel.DEBUG;
import static io.sentry.SentryLevel.ERROR;

import io.sentry.Hint;
import io.sentry.SentryEnvelope;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.UncaughtExceptionHandlerIntegration;
import io.sentry.android.core.AnrV2Integration;
import io.sentry.android.core.SentryAndroidOptions;
import io.sentry.android.core.internal.util.AndroidCurrentDateProvider;
import io.sentry.android.core.performance.AppStartMetrics;
import io.sentry.android.core.performance.TimeSpan;
import io.sentry.cache.EnvelopeCache;
import io.sentry.transport.ICurrentDateProvider;
import io.sentry.util.FileUtils;
import io.sentry.util.HintUtils;
import io.sentry.util.Objects;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

@ApiStatus.Internal
public final class AndroidEnvelopeCache extends EnvelopeCache {

  public static final String LAST_ANR_REPORT = "last_anr_report";

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

  @SuppressWarnings("deprecation")
  @Override
  public void store(@NotNull SentryEnvelope envelope, @NotNull Hint hint) {
    storeInternalAndroid(envelope, hint);
  }

  @Override
  public boolean storeEnvelope(@NotNull SentryEnvelope envelope, @NotNull Hint hint) {
    return storeInternalAndroid(envelope, hint);
  }

  private boolean storeInternalAndroid(@NotNull SentryEnvelope envelope, @NotNull Hint hint) {
    final boolean didStore = super.storeEnvelope(envelope, hint);

    final SentryAndroidOptions options = (SentryAndroidOptions) this.options;
    final TimeSpan sdkInitTimeSpan = AppStartMetrics.getInstance().getSdkInitTimeSpan();

    if (HintUtils.hasType(hint, UncaughtExceptionHandlerIntegration.UncaughtExceptionHint.class)
        && sdkInitTimeSpan.hasStarted()) {
      long timeSinceSdkInit =
          currentDateProvider.getCurrentTimeMillis() - sdkInitTimeSpan.getStartUptimeMs();
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

    HintUtils.runIfHasType(
        hint,
        AnrV2Integration.AnrV2Hint.class,
        (anrHint) -> {
          final @Nullable Long timestamp = anrHint.timestamp();
          options
              .getLogger()
              .log(
                  SentryLevel.DEBUG,
                  "Writing last reported ANR marker with timestamp %d",
                  timestamp);

          writeLastReportedAnrMarker(timestamp);
        });
    return didStore;
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
    final File crashMarkerFile = new File(outboxPath, STARTUP_CRASH_MARKER_FILE);
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

    final File crashMarkerFile = new File(outboxPath, STARTUP_CRASH_MARKER_FILE);
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

  public static @Nullable Long lastReportedAnr(final @NotNull SentryOptions options) {
    final String cacheDirPath =
        Objects.requireNonNull(
            options.getCacheDirPath(), "Cache dir path should be set for getting ANRs reported");

    final File lastAnrMarker = new File(cacheDirPath, LAST_ANR_REPORT);
    try {
      final String content = FileUtils.readText(lastAnrMarker);
      // we wrapped into try-catch already
      //noinspection ConstantConditions
      return content.equals("null") ? null : Long.parseLong(content.trim());
    } catch (Throwable e) {
      if (e instanceof FileNotFoundException) {
        options
            .getLogger()
            .log(DEBUG, "Last ANR marker does not exist. %s.", lastAnrMarker.getAbsolutePath());
      } else {
        options.getLogger().log(ERROR, "Error reading last ANR marker", e);
      }
    }
    return null;
  }

  private void writeLastReportedAnrMarker(final @Nullable Long timestamp) {
    final String cacheDirPath = options.getCacheDirPath();
    if (cacheDirPath == null) {
      options.getLogger().log(DEBUG, "Cache dir path is null, the ANR marker will not be written");
      return;
    }

    final File anrMarker = new File(cacheDirPath, LAST_ANR_REPORT);
    try (final OutputStream outputStream = new FileOutputStream(anrMarker)) {
      outputStream.write(String.valueOf(timestamp).getBytes(UTF_8));
      outputStream.flush();
    } catch (Throwable e) {
      options.getLogger().log(ERROR, "Error writing the ANR marker to the disk", e);
    }
  }
}
