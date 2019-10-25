package io.sentry.android.core;

import android.content.Context;
import android.os.Build;
import io.sentry.core.ILogger;
import io.sentry.core.SentryLevel;
import io.sentry.core.SentryOptions;
import java.io.File;
import java.lang.reflect.Method;

class AndroidOptionsInitializer {
  static void init(SentryOptions options, Context context) {
    init(options, context, new AndroidLogger());
  }

  static void init(SentryOptions options, Context context, ILogger logger) {
    // Firstly set the logger, if `debug=true` configured, logging can start asap.
    options.setLogger(logger);

    // TODO this needs to fetch the data from somewhere - defined at build time?
    options.setSentryClientName("sentry.java.android/0.0.1");

    ManifestMetadataReader.applyMetadata(context, options);
    createsEnvelopeDirPath(options, context);

    options.addEventProcessor(new DefaultAndroidEventProcessor(context, options));
    options.setSerializer(new AndroidSerializer(options.getLogger()));

    if (options.isEnableNdk() && isNdkAvailable()) {
      try {
        // TODO: Create Integrations interface and use that to initialize NDK
        Class<?> cls = Class.forName("io.sentry.android.ndk.SentryNdk");

        Method method = cls.getMethod("init", SentryOptions.class);
        Object[] args = new Object[1];
        args[0] = options;
        method.invoke(null, args);
      } catch (ClassNotFoundException e) {
        options.setEnableNdk(false);
        options.getLogger().log(SentryLevel.ERROR, "Failed to load SentryNdk.", e);
      } catch (Exception e) {
        options.setEnableNdk(false);
        options.getLogger().log(SentryLevel.ERROR, "Failed to initialize SentryNdk.", e);
      }
    }
  }

  private static void createsEnvelopeDirPath(SentryOptions options, Context context) {
    File cacheDir = context.getCacheDir().getAbsoluteFile();
    File envelopesDir = new File(cacheDir, "sentry-envelopes");
    if (!envelopesDir.exists()) {
      envelopesDir.mkdirs();
    }
    options.setCacheDirPath(envelopesDir.getAbsolutePath());
  }

  private static boolean isNdkAvailable() {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
  }
}
