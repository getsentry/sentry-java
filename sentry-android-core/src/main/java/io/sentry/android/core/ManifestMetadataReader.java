package io.sentry.android.core;

import static io.sentry.core.ILogger.log;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import io.sentry.core.ILogger;
import io.sentry.core.SentryLevel;
import io.sentry.core.SentryOptions;

class ManifestMetadataReader {

  static final String DSN_KEY = "io.sentry.dsn";
  static final String DEBUG_KEY = "io.sentry.debug";
  static final String AUTO_INIT = "io.sentry.auto-init";

  public static void applyMetadata(Context context, SentryOptions options) {
    if (context == null) throw new IllegalArgumentException("The application context is required.");

    try {
      Bundle metadata = getMetadata(context);

      if (metadata != null) {
        boolean debug = metadata.getBoolean(DEBUG_KEY, options.isDebug());
        log(options.getLogger(), SentryLevel.DEBUG, "debug read: %s", debug);
        options.setDebug(debug);

        String dsn = metadata.getString(DSN_KEY, null);
        if (dsn != null) {
          log(options.getLogger(), SentryLevel.DEBUG, "DSN read: %s", dsn);
          options.setDsn(dsn);
        }
      }
      log(
          options.getLogger(),
          SentryLevel.INFO,
          "Retrieving configuration from AndroidManifest.xml");
    } catch (Exception e) {
      log(
          options.getLogger(),
          SentryLevel.ERROR,
          "Failed to read configuration from android manifest metadata.",
          e);
    }
  }

  public static boolean isAutoInit(Context context, ILogger logger) {
    if (context == null) throw new IllegalArgumentException("The application context is required.");

    boolean autoInit = true;
    try {
      Bundle metadata = getMetadata(context);
      if (metadata != null) {
        autoInit = metadata.getBoolean(AUTO_INIT, true);
        log(logger, SentryLevel.DEBUG, "Auto-init: %s", autoInit);
      }
      log(logger, SentryLevel.INFO, "Retrieving auto-init from AndroidManifest.xml");
    } catch (Exception e) {
      log(logger, SentryLevel.ERROR, "Failed to read auto-init from android manifest metadata.", e);
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
