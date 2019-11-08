package io.sentry.android.core;

import static io.sentry.core.ILogger.logIfNotNull;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import io.sentry.core.ILogger;
import io.sentry.core.SentryLevel;

final class ManifestMetadataReader {

  private ManifestMetadataReader() {}

  static final String DSN_KEY = "io.sentry.dsn";
  static final String DEBUG_KEY = "io.sentry.debug";
  static final String ANR_ENABLE = "io.sentry.anr.enable";
  static final String ANR_REPORT_DEBUG = "io.sentry.anr.report-debug";
  static final String ANR_TIMEOUT_INTERVAL_MILLS = "io.sentry.anr.timeout-interval-mills";
  static final String AUTO_INIT = "io.sentry.auto-init";
  static final String ENABLE_NDK = "io.sentry.ndk.enable";

  static void applyMetadata(Context context, SentryAndroidOptions options) {
    if (context == null) throw new IllegalArgumentException("The application context is required.");

    try {
      Bundle metadata = getMetadata(context);

      if (metadata != null) {
        boolean debug = metadata.getBoolean(DEBUG_KEY, options.isDebug());
        logIfNotNull(options.getLogger(), SentryLevel.DEBUG, "debug read: %s", debug);
        options.setDebug(debug);

        boolean isAnrEnabled = metadata.getBoolean(ANR_ENABLE, options.isAnrEnabled());
        logIfNotNull(options.getLogger(), SentryLevel.DEBUG, "isAnrEnabled read: %s", isAnrEnabled);
        options.setAnrEnabled(isAnrEnabled);

        boolean isAnrReportInDebug =
            metadata.getBoolean(ANR_REPORT_DEBUG, options.isAnrReportInDebug());
        logIfNotNull(
            options.getLogger(),
            SentryLevel.DEBUG,
            "isAnrReportInDebug read: %s",
            isAnrReportInDebug);
        options.setAnrReportInDebug(isAnrReportInDebug);

        int anrTimeoutIntervalMills =
            metadata.getInt(ANR_TIMEOUT_INTERVAL_MILLS, options.getAnrTimeoutIntervalMills());
        logIfNotNull(
            options.getLogger(),
            SentryLevel.DEBUG,
            "anrTimeoutIntervalMills read: %d",
            anrTimeoutIntervalMills);
        options.setAnrTimeoutIntervalMills(anrTimeoutIntervalMills);

        String dsn = metadata.getString(DSN_KEY, null);
        if (dsn != null) {
          logIfNotNull(options.getLogger(), SentryLevel.DEBUG, "DSN read: %s", dsn);
          options.setDsn(dsn);
        }

        boolean ndk = metadata.getBoolean(ENABLE_NDK, options.isEnableNdk());
        logIfNotNull(options.getLogger(), SentryLevel.DEBUG, "NDK read: %s", ndk);
        options.setEnableNdk(ndk);
      }
      logIfNotNull(
          options.getLogger(),
          SentryLevel.INFO,
          "Retrieving configuration from AndroidManifest.xml");
    } catch (Exception e) {
      logIfNotNull(
          options.getLogger(),
          SentryLevel.ERROR,
          "Failed to read configuration from android manifest metadata.",
          e);
    }
  }

  static boolean isAutoInit(Context context, ILogger logger) {
    if (context == null) throw new IllegalArgumentException("The application context is required.");

    boolean autoInit = true;
    try {
      Bundle metadata = getMetadata(context);
      if (metadata != null) {
        autoInit = metadata.getBoolean(AUTO_INIT, true);
        logIfNotNull(logger, SentryLevel.DEBUG, "Auto-init: %s", autoInit);
      }
      logIfNotNull(logger, SentryLevel.INFO, "Retrieving auto-init from AndroidManifest.xml");
    } catch (Exception e) {
      logIfNotNull(
          logger, SentryLevel.ERROR, "Failed to read auto-init from android manifest metadata.", e);
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
