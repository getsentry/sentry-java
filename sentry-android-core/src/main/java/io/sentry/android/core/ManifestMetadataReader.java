package io.sentry.android.core;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import io.sentry.core.ILogger;
import io.sentry.core.SentryLevel;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;

final class ManifestMetadataReader {

  static final String DSN_KEY = "io.sentry.dsn";
  static final String DEBUG_KEY = "io.sentry.debug";
  static final String DEBUG_LEVEL = "io.sentry.debug.level";
  static final String ANR_ENABLE = "io.sentry.anr.enable";
  static final String ANR_REPORT_DEBUG = "io.sentry.anr.report-debug";
  static final String ANR_TIMEOUT_INTERVAL_MILLS = "io.sentry.anr.timeout-interval-mills";
  static final String AUTO_INIT = "io.sentry.auto-init";
  static final String ENABLE_NDK = "io.sentry.ndk.enable";

  private ManifestMetadataReader() {}

  static void applyMetadata(Context context, SentryAndroidOptions options) {
    if (context == null) throw new IllegalArgumentException("The application context is required.");

    try {
      Bundle metadata = getMetadata(context);

      if (metadata != null) {
        boolean debug = metadata.getBoolean(DEBUG_KEY, options.isDebug());
        options.setDebug(debug);
        options.getLogger().log(SentryLevel.DEBUG, "debug read: %s", debug);

        if (options.isDebug()) {
          String level =
              metadata.getString(
                  DEBUG_LEVEL, options.getDiagnosticLevel().name().toLowerCase(Locale.ROOT));
          options.setDiagnosticLevel(SentryLevel.valueOf(level.toUpperCase(Locale.ROOT)));
        }

        boolean isAnrEnabled = metadata.getBoolean(ANR_ENABLE, options.isAnrEnabled());
        options.getLogger().log(SentryLevel.DEBUG, "isAnrEnabled read: %s", isAnrEnabled);
        options.setAnrEnabled(isAnrEnabled);

        boolean isAnrReportInDebug =
            metadata.getBoolean(ANR_REPORT_DEBUG, options.isAnrReportInDebug());
        options
            .getLogger()
            .log(SentryLevel.DEBUG, "isAnrReportInDebug read: %s", isAnrReportInDebug);
        options.setAnrReportInDebug(isAnrReportInDebug);

        long anrTimeoutIntervalMills =
            metadata.getLong(ANR_TIMEOUT_INTERVAL_MILLS, options.getAnrTimeoutIntervalMills());
        options
            .getLogger()
            .log(SentryLevel.DEBUG, "anrTimeoutIntervalMills read: %d", anrTimeoutIntervalMills);
        options.setAnrTimeoutIntervalMills(anrTimeoutIntervalMills);

        String dsn = metadata.getString(DSN_KEY, null);
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

        boolean ndk = metadata.getBoolean(ENABLE_NDK, options.isEnableNdk());
        options.getLogger().log(SentryLevel.DEBUG, "NDK read: %s", ndk);
        options.setEnableNdk(ndk);
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

  static boolean isAutoInit(Context context, final @NotNull ILogger logger) {
    if (context == null) throw new IllegalArgumentException("The application context is required.");

    boolean autoInit = true;
    try {
      Bundle metadata = getMetadata(context);
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

  private static Bundle getMetadata(Context context) throws PackageManager.NameNotFoundException {
    ApplicationInfo app =
        context
            .getPackageManager()
            .getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
    return app.metaData;
  }
}
