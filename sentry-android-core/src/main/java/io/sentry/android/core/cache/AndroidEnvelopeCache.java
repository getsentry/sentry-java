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
import io.sentry.android.core.TombstoneIntegration;
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
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

@ApiStatus.Internal
public final class AndroidEnvelopeCache extends EnvelopeCache {

  public static final String LAST_ANR_REPORT = "last_anr_report";
  public static final String LAST_TOMBSTONE_REPORT = "last_tombstone_report";
  public static final String LAST_APP_START_REPORT = "last_app_start_report";

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

    for (TimestampMarkerHandler<?> handler : TIMESTAMP_MARKER_HANDLERS) {
      handler.handle(this, hint, options);
    }

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
      final boolean exists =
          options.getRuntimeManager().runWithRelaxedPolicy(() -> crashMarkerFile.exists());
      if (exists) {
        if (!options.getRuntimeManager().runWithRelaxedPolicy(() -> crashMarkerFile.delete())) {
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

  private static @Nullable Long lastReportedMarker(
      final @NotNull SentryOptions options,
      @NotNull String reportFilename,
      @NotNull String markerLabel) {
    final String cacheDirPath =
        Objects.requireNonNull(
            options.getCacheDirPath(),
            "Cache dir path should be set for getting " + markerLabel + "s reported");

    final File lastMarker = new File(cacheDirPath, reportFilename);
    try {
      final String content = FileUtils.readText(lastMarker);
      // we wrapped into try-catch already
      //noinspection ConstantConditions
      return (content == null || content.equals("null")) ? null : Long.parseLong(content.trim());
    } catch (Throwable e) {
      if (e instanceof FileNotFoundException) {
        options
            .getLogger()
            .log(
                DEBUG,
                "Last " + markerLabel + " marker does not exist. %s.",
                lastMarker.getAbsolutePath());
      } else {
        options.getLogger().log(ERROR, "Error reading last " + markerLabel + " marker", e);
      }
    }
    return null;
  }

  private void writeLastReportedMarker(
      final @Nullable Long timestamp,
      @NotNull String reportFilename,
      @NotNull String markerCategory) {
    final String cacheDirPath = options.getCacheDirPath();
    if (cacheDirPath == null) {
      options
          .getLogger()
          .log(
              DEBUG,
              "Cache dir path is null, the " + markerCategory + " marker will not be written");
      return;
    }

    final File anrMarker = new File(cacheDirPath, reportFilename);
    try (final OutputStream outputStream = new FileOutputStream(anrMarker)) {
      outputStream.write(String.valueOf(timestamp).getBytes(UTF_8));
      outputStream.flush();
    } catch (Throwable e) {
      options
          .getLogger()
          .log(ERROR, "Error writing the " + markerCategory + " marker to the disk", e);
    }
  }

  public static @Nullable Long lastReportedAnr(final @NotNull SentryOptions options) {
    return lastReportedMarker(options, LAST_ANR_REPORT, LAST_ANR_MARKER_LABEL);
  }

  public static @Nullable Long lastReportedTombstone(final @NotNull SentryOptions options) {
    return lastReportedMarker(options, LAST_TOMBSTONE_REPORT, LAST_TOMBSTONE_MARKER_LABEL);
  }

  public static @Nullable Long lastReportedAppStart(final @NotNull SentryOptions options) {
    return lastReportedMarker(options, LAST_APP_START_REPORT, LAST_APP_START_MARKER_LABEL);
  }

  public static void storeAppStartTimestamp(
      final @NotNull SentryOptions options, final long timestamp) {
    final String cacheDirPath = options.getCacheDirPath();
    if (cacheDirPath == null) {
      options
          .getLogger()
          .log(DEBUG, "Cache dir path is null, the App Start marker will not be written");
      return;
    }

    final File marker = new File(cacheDirPath, LAST_APP_START_REPORT);
    try (final OutputStream outputStream = new FileOutputStream(marker)) {
      outputStream.write(String.valueOf(timestamp).getBytes(UTF_8));
      outputStream.flush();
    } catch (Throwable e) {
      options.getLogger().log(ERROR, "Error writing the App Start marker to the disk", e);
    }
  }

  private static final class TimestampMarkerHandler<T> {
    interface TimestampExtractor<T> {
      @NotNull
      Long extract(T value);
    }

    private final @NotNull Class<T> type;
    private final @NotNull String label;
    private final @NotNull String reportFilename;
    private final @NotNull TimestampExtractor<T> timestampProvider;

    TimestampMarkerHandler(
        final @NotNull Class<T> type,
        final @NotNull String label,
        final @NotNull String reportFilename,
        final @NotNull TimestampExtractor<T> timestampProvider) {
      this.type = type;
      this.label = label;
      this.reportFilename = reportFilename;
      this.timestampProvider = timestampProvider;
    }

    void handle(
        final @NotNull AndroidEnvelopeCache cache,
        final @NotNull Hint hint,
        final @NotNull SentryAndroidOptions options) {
      HintUtils.runIfHasType(
          hint,
          type,
          (typedHint) -> {
            final @NotNull Long timestamp = timestampProvider.extract(typedHint);
            options
                .getLogger()
                .log(
                    SentryLevel.DEBUG,
                    "Writing last reported %s marker with timestamp %d",
                    label,
                    timestamp);
            cache.writeLastReportedMarker(timestamp, reportFilename, label);
          });
    }
  }

  public static final String LAST_TOMBSTONE_MARKER_LABEL = "Tombstone";
  public static final String LAST_ANR_MARKER_LABEL = "ANR";
  public static final String LAST_APP_START_MARKER_LABEL = "App Start";
  private static final List<TimestampMarkerHandler<?>> TIMESTAMP_MARKER_HANDLERS =
      Arrays.asList(
          new TimestampMarkerHandler<>(
              AnrV2Integration.AnrV2Hint.class,
              LAST_ANR_MARKER_LABEL,
              LAST_ANR_REPORT,
              anrV2Hint -> anrV2Hint.timestamp()),
          new TimestampMarkerHandler<>(
              TombstoneIntegration.TombstoneHint.class,
              LAST_TOMBSTONE_MARKER_LABEL,
              LAST_TOMBSTONE_REPORT,
              tombstoneHint -> tombstoneHint.timestamp()));
}
