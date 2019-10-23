package io.sentry.android.core;

import android.content.Context;
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

    if (options.isEnableNdk()) {
      try {
        // TODO: Create Integrations interface and use that to initialize NDK
        Class<?> cls = Class.forName("io.sentry.android.ndk.SentryNdk");

        // TODO: temporary hack
        String cacheDirPath = context.getCacheDir().getAbsolutePath() + "/sentry-envelopes";
        File f = new File(cacheDirPath);
        f.mkdirs();

        Method method = cls.getMethod("init", SentryOptions.class, String.class);
        Object[] args = new Object[2];
        args[0] = options;
        args[1] = cacheDirPath;
        method.invoke(null, args);
      } catch (ClassNotFoundException exc) {
        options.getLogger().log(SentryLevel.ERROR, "Failed to load SentryNdk.");
      } catch (Exception e) {
        options.getLogger().log(SentryLevel.ERROR, "Failed to initialize SentryNdk.", e);
      }
    }

    options.addEventProcessor(new DefaultAndroidEventProcessor(context, options));
    options.setSerializer(new AndroidSerializer(options.getLogger()));
  }

  private static void createsEnvelopeDirPath(SentryOptions options, Context context) {
    File cacheDir = context.getCacheDir().getAbsoluteFile();
    File envelopesDir = new File(cacheDir, "sentry-envelopes");
    if (!envelopesDir.exists()) {
      envelopesDir.mkdirs();
    }
    options.setCacheDirPath(envelopesDir.getAbsolutePath());
  }
}
