package io.sentry.android.core;

import io.sentry.IHub;
import io.sentry.Integration;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.util.Objects;
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
    final SentryAndroidOptions androidOptions =
        Objects.requireNonNull(
            (options instanceof SentryAndroidOptions) ? (SentryAndroidOptions) options : null,
            "SentryAndroidOptions is required");

    final boolean enabled = androidOptions.isEnableNdk();
    androidOptions.getLogger().log(SentryLevel.DEBUG, "NdkIntegration enabled: %s", enabled);

    // Note: `hub` isn't used here because the NDK integration writes files to disk which are picked
    // up by another integration (EnvelopeFileObserverIntegration).
    if (enabled && sentryNdkClass != null) {
      final String cachedDir = androidOptions.getCacheDirPath();
      if (cachedDir == null || cachedDir.isEmpty()) {
        androidOptions
            .getLogger()
            .log(SentryLevel.ERROR, "No cache dir path is defined in options.");
        androidOptions.setEnableNdk(false);
        return;
      }

      try {
        final Method method = sentryNdkClass.getMethod("init", SentryAndroidOptions.class);
        final Object[] args = new Object[1];
        args[0] = androidOptions;
        method.invoke(null, args);

        androidOptions.getLogger().log(SentryLevel.DEBUG, "NdkIntegration installed.");
      } catch (NoSuchMethodException e) {
        androidOptions.setEnableNdk(false);
        androidOptions
            .getLogger()
            .log(SentryLevel.ERROR, "Failed to invoke the SentryNdk.init method.", e);
      } catch (Throwable e) {
        androidOptions.setEnableNdk(false);
        androidOptions.getLogger().log(SentryLevel.ERROR, "Failed to initialize SentryNdk.", e);
      }
    } else {
      androidOptions.setEnableNdk(false);
    }
  }

  @TestOnly
  @Nullable
  Class<?> getSentryNdkClass() {
    return sentryNdkClass;
  }
}
