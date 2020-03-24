package io.sentry.android.core;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import io.sentry.core.ILogger;
import io.sentry.core.SentryLevel;
import io.sentry.core.util.Objects;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;

/** Class responsible for reading values from manifest and setting them to the options */
final class ManifestMetadataReader {

  static final String DSN = "io.sentry.dsn";
  static final String DEBUG = "io.sentry.debug";
  static final String DEBUG_LEVEL = "io.sentry.debug.level";
  static final String SAMPLE_RATE = "io.sentry.sample-rate";
  static final String ANR_ENABLE = "io.sentry.anr.enable";
  static final String ANR_REPORT_DEBUG = "io.sentry.anr.report-debug";

  @Deprecated
  static final String ANR_TIMEOUT_INTERVAL_MILLS = "io.sentry.anr.timeout-interval-mills";

  static final String ANR_TIMEOUT_INTERVAL_MILLIS = "io.sentry.anr.timeout-interval-millis";

  static final String AUTO_INIT = "io.sentry.auto-init";
  static final String NDK_ENABLE = "io.sentry.ndk.enable";
  static final String RELEASE = "io.sentry.release";
  static final String ENVIRONMENT = "io.sentry.environment";
  static final String SESSION_TRACKING_ENABLE = "io.sentry.session-tracking.enable";
  static final String SESSION_TRACKING_TIMEOUT_INTERVAL_MILLIS =
      "io.sentry.session-tracking.timeout-interval-millis";

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

      if (metadata != null) {
        final boolean debug = metadata.getBoolean(DEBUG, options.isDebug());
        options.setDebug(debug);
        options.getLogger().log(SentryLevel.DEBUG, "debug read: %s", debug);

        if (options.isDebug()) {
          final String level =
              metadata.getString(
                  DEBUG_LEVEL, options.getDiagnosticLevel().name().toLowerCase(Locale.ROOT));
          options.setDiagnosticLevel(SentryLevel.valueOf(level.toUpperCase(Locale.ROOT)));
        }

        final boolean anrEnabled = metadata.getBoolean(ANR_ENABLE, options.isAnrEnabled());
        options.getLogger().log(SentryLevel.DEBUG, "anrEnabled read: %s", anrEnabled);
        options.setAnrEnabled(anrEnabled);

        final boolean sessionTrackingEnabled =
            metadata.getBoolean(SESSION_TRACKING_ENABLE, options.isEnableSessionTracking());
        options
            .getLogger()
            .log(SentryLevel.DEBUG, "sessionTrackingEnabled read: %s", sessionTrackingEnabled);
        options.setEnableSessionTracking(sessionTrackingEnabled);

        if (options.getSampleRate() == null) {
          Double sampleRate = metadata.getDouble(SAMPLE_RATE, -1);
          options.getLogger().log(SentryLevel.DEBUG, "sampleRate read: %s", sampleRate);
          if (sampleRate != -1) {
            options.setSampleRate(sampleRate);
          }
        }

        boolean anrReportInDebug =
            metadata.getBoolean(ANR_REPORT_DEBUG, options.isAnrReportInDebug());
        options.getLogger().log(SentryLevel.DEBUG, "anrReportInDebug read: %s", anrReportInDebug);
        options.setAnrReportInDebug(anrReportInDebug);

        // deprecated
        final long anrTimeoutIntervalMills =
            metadata.getInt(
                ANR_TIMEOUT_INTERVAL_MILLS, (int) options.getAnrTimeoutIntervalMillis());

        // reading new values, but deprecated one as fallback
        final long anrTimeoutIntervalMillis =
            metadata.getInt(ANR_TIMEOUT_INTERVAL_MILLIS, (int) anrTimeoutIntervalMills);

        options
            .getLogger()
            .log(SentryLevel.DEBUG, "anrTimeoutIntervalMillis read: %d", anrTimeoutIntervalMillis);
        options.setAnrTimeoutIntervalMillis(anrTimeoutIntervalMillis);

        final String dsn = metadata.getString(DSN, null);
        if (dsn == null) {
          options
              .getLogger()
              .log(SentryLevel.FATAL, "DSN is required. Use empty string to disable SDK.");
        } else if (dsn.isEmpty()) {
          options.getLogger().log(SentryLevel.DEBUG, "DSN is empty, disabling sentry-android");
        } else {
          options.getLogger().log(SentryLevel.DEBUG, "DSN read: %s", dsn);
        }
        options.setDsn(dsn);

        final boolean ndk = metadata.getBoolean(NDK_ENABLE, options.isEnableNdk());
        options.getLogger().log(SentryLevel.DEBUG, "NDK read: %s", ndk);
        options.setEnableNdk(ndk);

        final String release = metadata.getString(RELEASE, options.getRelease());
        options.getLogger().log(SentryLevel.DEBUG, "release read: %s", release);
        options.setRelease(release);

        final String environment = metadata.getString(ENVIRONMENT, options.getEnvironment());
        options.getLogger().log(SentryLevel.DEBUG, "environment read: %s", environment);
        options.setEnvironment(environment);

        final long sessionTrackingTimeoutIntervalMillis =
            metadata.getInt(
                SESSION_TRACKING_TIMEOUT_INTERVAL_MILLIS,
                (int) options.getSessionTrackingIntervalMillis());
        options
            .getLogger()
            .log(
                SentryLevel.DEBUG,
                "sessionTrackingTimeoutIntervalMillis read: %d",
                sessionTrackingTimeoutIntervalMillis);
        options.setSessionTrackingIntervalMillis(sessionTrackingTimeoutIntervalMillis);
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
        autoInit = metadata.getBoolean(AUTO_INIT, true);
        logger.log(SentryLevel.DEBUG, "Auto-init: %s", autoInit);
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
  private static Bundle getMetadata(final @NotNull Context context)
      throws PackageManager.NameNotFoundException {
    final ApplicationInfo app =
        context
            .getPackageManager()
            .getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
    return app.metaData;
  }
}
