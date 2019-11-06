package io.sentry.android.core;

import android.os.Build;
import io.sentry.core.IHub;
import io.sentry.core.Integration;
import io.sentry.core.SentryLevel;
import io.sentry.core.SentryOptions;
import java.lang.reflect.Method;

final class NdkIntegration implements Integration {
  private static boolean isNdkAvailable() {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
  }

  @Override
  public void register(IHub hub, SentryOptions options) {
    // Note: `hub` isn't used here because the NDK integration writes files to disk which are picked
    // up by another
    // integration. The NDK directory watching must happen before this integration runs.
    if (options.isEnableNdk() && isNdkAvailable()) {
      try {
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
}
