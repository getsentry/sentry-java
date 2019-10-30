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

    options.setSentryClientName(BuildConfig.SENTRY_CLIENT_NAME + "/" + BuildConfig.VERSION_NAME);

    ManifestMetadataReader.applyMetadata(context, options);
    initializeCacheDirs(context, options);

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

  private static void initializeCacheDirs(Context context, SentryOptions options) {
    File cacheDir = new File(context.getCacheDir(), "sentry");
    cacheDir.mkdirs();
    options.setCacheDirPath(cacheDir.getAbsolutePath());
    (new File(options.getOutboxPath())).mkdirs();
  }

  private static boolean isNdkAvailable() {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
  }
}
