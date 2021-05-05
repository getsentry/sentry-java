package io.sentry.android.core;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import io.sentry.ILogger;
import io.sentry.SentryLevel;
import io.sentry.util.Objects;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Class responsible for reading values from manifest and setting them to the options */
final class ManifestMetadataReader {

  static final String DSN = "io.sentry.dsn";
  static final String DEBUG = "io.sentry.debug";
  static final String DEBUG_LEVEL = "io.sentry.debug.level";
  static final String SAMPLE_RATE = "io.sentry.sample-rate";
  static final String ANR_ENABLE = "io.sentry.anr.enable";
  static final String ANR_REPORT_DEBUG = "io.sentry.anr.report-debug";

  static final String ANR_TIMEOUT_INTERVAL_MILLIS = "io.sentry.anr.timeout-interval-millis";

  static final String AUTO_INIT = "io.sentry.auto-init";
  static final String NDK_ENABLE = "io.sentry.ndk.enable";
  static final String NDK_SCOPE_SYNC_ENABLE = "io.sentry.ndk.scope-sync.enable";
  static final String RELEASE = "io.sentry.release";
  static final String ENVIRONMENT = "io.sentry.environment";
  static final String SESSION_TRACKING_ENABLE = "io.sentry.session-tracking.enable";
  static final String SESSION_TRACKING_TIMEOUT_INTERVAL_MILLIS =
      "io.sentry.session-tracking.timeout-interval-millis";

  static final String BREADCRUMBS_ACTIVITY_LIFECYCLE_ENABLE =
      "io.sentry.breadcrumbs.activity-lifecycle";
  static final String BREADCRUMBS_APP_LIFECYCLE_ENABLE = "io.sentry.breadcrumbs.app-lifecycle";
  static final String BREADCRUMBS_SYSTEM_EVENTS_ENABLE = "io.sentry.breadcrumbs.system-events";
  static final String BREADCRUMBS_APP_COMPONENTS_ENABLE = "io.sentry.breadcrumbs.app-components";

  static final String UNCAUGHT_EXCEPTION_HANDLER_ENABLE =
      "io.sentry.uncaught-exception-handler.enable";

  static final String TRACES_SAMPLE_RATE = "io.sentry.traces.sample-rate";
  static final String TRACES_ACTIVITY_ENABLE = "io.sentry.traces.activity.enable";
  static final String TRACES_ACTIVITY_AUTO_FINISH_ENABLE =
      "io.sentry.traces.activity.auto-finish.enable";

  static final String ATTACH_THREADS = "io.sentry.attach-threads";

  /** ManifestMetadataReader ctor */
  private ManifestMetadataReader() {}

  /**
   * Reads configurations from Manifest and sets it to the options
   *
   * @param context the application context
   * @param options the SentryAndroidOptions
   */
  static void applyMetadata(
      final @NotNull Context context, final @NotNull SentryAndroidOptions options) {
    Objects.requireNonNull(context, "The application context is required.");
    Objects.requireNonNull(options, "The options object is required.");

    try {
      final Bundle metadata = getMetadata(context);
      final ILogger logger = options.getLogger();

      if (metadata != null) {
        options.setDebug(readBool(metadata, logger, DEBUG, options.isDebug()));

        if (options.isDebug()) {
          final String level =
              readString(
                  metadata,
                  logger,
                  DEBUG_LEVEL,
                  options.getDiagnosticLevel().name().toLowerCase(Locale.ROOT));
          if (level != null) {
            options.setDiagnosticLevel(SentryLevel.valueOf(level.toUpperCase(Locale.ROOT)));
          }
        }

        options.setAnrEnabled(readBool(metadata, logger, ANR_ENABLE, options.isAnrEnabled()));

        options.setEnableSessionTracking(
            readBool(metadata, logger, SESSION_TRACKING_ENABLE, options.isEnableSessionTracking()));

        if (options.getSampleRate() == null) {
          final Double sampleRate = readDouble(metadata, logger, SAMPLE_RATE);
          if (sampleRate != -1) {
            options.setSampleRate(sampleRate);
          }
        }

        options.setAnrReportInDebug(
            readBool(metadata, logger, ANR_REPORT_DEBUG, options.isAnrReportInDebug()));

        options.setAnrTimeoutIntervalMillis(
            readLong(
                metadata,
                logger,
                ANR_TIMEOUT_INTERVAL_MILLIS,
                options.getAnrTimeoutIntervalMillis()));

        final String dsn = readString(metadata, logger, DSN, options.getDsn());
        if (dsn == null) {
          options
              .getLogger()
              .log(SentryLevel.FATAL, "DSN is required. Use empty string to disable SDK.");
        } else if (dsn.isEmpty()) {
          options.getLogger().log(SentryLevel.DEBUG, "DSN is empty, disabling sentry-android");
        }
        options.setDsn(dsn);

        options.setEnableNdk(readBool(metadata, logger, NDK_ENABLE, options.isEnableNdk()));

        options.setEnableScopeSync(
            readBool(metadata, logger, NDK_SCOPE_SYNC_ENABLE, options.isEnableScopeSync()));

        options.setRelease(readString(metadata, logger, RELEASE, options.getRelease()));

        options.setEnvironment(readString(metadata, logger, ENVIRONMENT, options.getEnvironment()));

        options.setSessionTrackingIntervalMillis(
            readLong(
                metadata,
                logger,
                SESSION_TRACKING_TIMEOUT_INTERVAL_MILLIS,
                options.getSessionTrackingIntervalMillis()));

        options.setEnableActivityLifecycleBreadcrumbs(
            readBool(
                metadata,
                logger,
                BREADCRUMBS_ACTIVITY_LIFECYCLE_ENABLE,
                options.isEnableActivityLifecycleBreadcrumbs()));

        options.setEnableAppLifecycleBreadcrumbs(
            readBool(
                metadata,
                logger,
                BREADCRUMBS_APP_LIFECYCLE_ENABLE,
                options.isEnableAppComponentBreadcrumbs()));

        options.setEnableSystemEventBreadcrumbs(
            readBool(
                metadata,
                logger,
                BREADCRUMBS_SYSTEM_EVENTS_ENABLE,
                options.isEnableSystemEventBreadcrumbs()));

        options.setEnableAppComponentBreadcrumbs(
            readBool(
                metadata,
                logger,
                BREADCRUMBS_APP_COMPONENTS_ENABLE,
                options.isEnableAppComponentBreadcrumbs()));

        options.setEnableUncaughtExceptionHandler(
            readBool(
                metadata,
                logger,
                UNCAUGHT_EXCEPTION_HANDLER_ENABLE,
                options.isEnableUncaughtExceptionHandler()));

        options.setAttachThreads(
            readBool(metadata, logger, ATTACH_THREADS, options.isAttachThreads()));

        if (options.getTracesSampleRate() == null) {
          final Double tracesSampleRate = readDouble(metadata, logger, TRACES_SAMPLE_RATE);
          if (tracesSampleRate != -1) {
            options.setTracesSampleRate(tracesSampleRate);
          }
        }

        options.setEnableAutoActivityLifecycleTracing(
            readBool(
                metadata,
                logger,
                TRACES_ACTIVITY_ENABLE,
                options.isEnableAutoActivityLifecycleTracing()));

        options.setEnableActivityLifecycleTracingAutoFinish(
            readBool(
                metadata,
                logger,
                TRACES_ACTIVITY_AUTO_FINISH_ENABLE,
                options.isEnableActivityLifecycleTracingAutoFinish()));
      }
      options
          .getLogger()
          .log(SentryLevel.INFO, "Retrieving configuration from AndroidManifest.xml");
    } catch (Exception e) {
      options
          .getLogger()
          .log(
              SentryLevel.ERROR, "Failed to read configuration from android manifest metadata.", e);
    }
  }

  private static boolean readBool(
      final @NotNull Bundle metadata,
      final @NotNull ILogger logger,
      final @NotNull String key,
      final boolean defaultValue) {
    final boolean value = metadata.getBoolean(key, defaultValue);
    logger.log(SentryLevel.DEBUG, "%s read: %s", key, value);
    return value;
  }

  private static @Nullable String readString(
      final @NotNull Bundle metadata,
      final @NotNull ILogger logger,
      final @NotNull String key,
      final @Nullable String defaultValue) {
    final String value = metadata.getString(key, defaultValue);
    logger.log(SentryLevel.DEBUG, "%s read: %s", key, value);
    return value;
  }

  private static @NotNull Double readDouble(
      final @NotNull Bundle metadata, final @NotNull ILogger logger, final @NotNull String key) {
    // manifest meta-data only reads float
    final Double value = ((Float) metadata.getFloat(key, -1)).doubleValue();
    logger.log(SentryLevel.DEBUG, "%s read: %s", key, value);
    return value;
  }

  private static long readLong(
      final @NotNull Bundle metadata,
      final @NotNull ILogger logger,
      final @NotNull String key,
      final long defaultValue) {
    // manifest meta-data only reads int if the value is not big enough
    final long value = metadata.getInt(key, (int) defaultValue);
    logger.log(SentryLevel.DEBUG, "%s read: %s", key, value);
    return value;
  }

  /**
   * Checks if auto init is enabled or disabled
   *
   * @param context the application context
   * @param logger the Logger interface
   * @return true if auto init is enabled or false otherwise
   */
  static boolean isAutoInit(final @NotNull Context context, final @NotNull ILogger logger) {
    Objects.requireNonNull(context, "The application context is required.");

    boolean autoInit = true;
    try {
      final Bundle metadata = getMetadata(context);
      if (metadata != null) {
        autoInit = readBool(metadata, logger, AUTO_INIT, true);
      }
      logger.log(SentryLevel.INFO, "Retrieving auto-init from AndroidManifest.xml");
    } catch (Exception e) {
      logger.log(SentryLevel.ERROR, "Failed to read auto-init from android manifest metadata.", e);
    }
    return autoInit;
  }

  /**
   * Returns the Bundle attached from the given Context
   *
   * @param context the application context
   * @return the Bundle attached to the PackageManager
   * @throws PackageManager.NameNotFoundException if the package name is non-existent
   */
  private static @Nullable Bundle getMetadata(final @NotNull Context context)
      throws PackageManager.NameNotFoundException {
    final ApplicationInfo app =
        context
            .getPackageManager()
            .getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
    return app.metaData;
  }
}
