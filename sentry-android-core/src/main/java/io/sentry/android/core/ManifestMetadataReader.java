package io.sentry.android.core;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import io.sentry.core.SentryLevel;
import io.sentry.core.SentryOptions;

class ManifestMetadataReader {

  static final String DSN_KEY = "io.sentry.dsn";
  static final String DEBUG_KEY = "io.sentry.debug";

  public static void applyMetadata(Context context, SentryOptions options) {
    try {
      ApplicationInfo app =
          context
              .getPackageManager()
              .getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
      Bundle metadata = app.metaData;

      if (metadata != null) {
        options.setDebug(metadata.getBoolean(DEBUG_KEY, options.isDebug()));
        String dsn = metadata.getString(DSN_KEY, null);
        if (dsn != null) {
          options.getLogger().log(SentryLevel.DEBUG, "DSN read: %s", dsn);
          options.setDsn(dsn);
        }
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
}
