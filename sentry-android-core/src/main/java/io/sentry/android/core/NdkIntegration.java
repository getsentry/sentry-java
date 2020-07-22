package io.sentry.android.core;

import io.sentry.core.IHub;
import io.sentry.core.Integration;
import io.sentry.core.SentryLevel;
import io.sentry.core.SentryOptions;
import io.sentry.core.util.Objects;
import java.lang.reflect.Method;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/** Enables the NDK error reporting for Android */
public final class NdkIntegration implements Integration {

  public static final String SENTRY_NDK_CLASS_NAME = "io.sentry.android.ndk.SentryNdk";

  private final @Nullable Class<?> sentryNdkClass;

  public NdkIntegration(final @Nullable Class<?> sentryNdkClass) {
    this.sentryNdkClass = sentryNdkClass;
  }

  @Override
  public final void register(final @NotNull IHub hub, final @NotNull SentryOptions options) {
    Objects.requireNonNull(hub, "Hub is required");
    Objects.requireNonNull(options, "SentryOptions is required");

    final boolean enabled = options.isEnableNdk();
    options.getLogger().log(SentryLevel.DEBUG, "NdkIntegration enabled: %s", enabled);

    // Note: `hub` isn't used here because the NDK integration writes files to disk which are picked
    // up by another integration (EnvelopeFileObserverIntegration).
    if (enabled && sentryNdkClass != null) {
      final String cachedDir = options.getCacheDirPath();
      if (cachedDir == null || cachedDir.isEmpty()) {
        options.getLogger().log(SentryLevel.ERROR, "No cache dir path is defined in options.");
        options.setEnableNdk(false);
        return;
      }

      try {
        final Method method = sentryNdkClass.getMethod("init", SentryOptions.class);
        final Object[] args = new Object[1];
        args[0] = options;
        method.invoke(null, args);

        options.getLogger().log(SentryLevel.DEBUG, "NdkIntegration installed.");
      } catch (NoSuchMethodException e) {
        options.setEnableNdk(false);
        options
            .getLogger()
            .log(SentryLevel.ERROR, "Failed to invoke the SentryNdk.init method.", e);
      } catch (Throwable e) {
        options.setEnableNdk(false);
        options.getLogger().log(SentryLevel.ERROR, "Failed to initialize SentryNdk.", e);
      }
    } else {
      options.setEnableNdk(false);
    }
  }

  @TestOnly
  @Nullable
  Class<?> getSentryNdkClass() {
    return sentryNdkClass;
  }
}
