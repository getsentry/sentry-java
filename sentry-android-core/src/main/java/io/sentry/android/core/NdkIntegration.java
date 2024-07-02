package io.sentry.android.core;

import static io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion;

import io.sentry.IHub;
import io.sentry.Integration;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.util.Objects;
import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Method;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/** Enables the NDK error reporting for Android */
public final class NdkIntegration implements Integration, Closeable {

  public static final String SENTRY_NDK_CLASS_NAME = "io.sentry.android.ndk.SentryNdk";

  private final @Nullable Class<?> sentryNdkClass;

  private @Nullable SentryAndroidOptions options;

  public NdkIntegration(final @Nullable Class<?> sentryNdkClass) {
    this.sentryNdkClass = sentryNdkClass;
  }

  @Override
  public final void register(final @NotNull IHub hub, final @NotNull SentryOptions options) {
    Objects.requireNonNull(hub, "Hub is required");
    this.options =
        Objects.requireNonNull(
            (options instanceof SentryAndroidOptions) ? (SentryAndroidOptions) options : null,
            "SentryAndroidOptions is required");

    final boolean enabled = this.options.isEnableNdk();
    this.options.getLogger().log(SentryLevel.DEBUG, "NdkIntegration enabled: %s", enabled);

    // Note: `hub` isn't used here because the NDK integration writes files to disk which are picked
    // up by another integration (EnvelopeFileObserverIntegration).
    if (enabled && sentryNdkClass != null) {
      final String cachedDir = this.options.getCacheDirPath();
      if (cachedDir == null) {
        this.options.getLogger().log(SentryLevel.ERROR, "No cache dir path is defined in options.");
        disableNdkIntegration(this.options);
        return;
      }

      try {
        final Method method = sentryNdkClass.getMethod("init", SentryAndroidOptions.class);
        final Object[] args = new Object[1];
        args[0] = this.options;
        method.invoke(null, args);

        this.options.getLogger().log(SentryLevel.DEBUG, "NdkIntegration installed.");
        addIntegrationToSdkVersion(getClass());
      } catch (NoSuchMethodException e) {
        disableNdkIntegration(this.options);
        this.options
            .getLogger()
            .log(SentryLevel.ERROR, "Failed to invoke the SentryNdk.init method.", e);
      } catch (Throwable e) {
        disableNdkIntegration(this.options);
        this.options.getLogger().log(SentryLevel.ERROR, "Failed to initialize SentryNdk.", e);
      }
    } else {
      disableNdkIntegration(this.options);
    }
  }

  private void disableNdkIntegration(final @NotNull SentryAndroidOptions options) {
    options.setEnableNdk(false);
    options.setEnableScopeSync(false);
  }

  @TestOnly
  @Nullable
  Class<?> getSentryNdkClass() {
    return sentryNdkClass;
  }

  @Override
  public void close() throws IOException {
    if (options != null && options.isEnableNdk() && sentryNdkClass != null) {
      try {
        final Method method = sentryNdkClass.getMethod("close");
        method.invoke(null, new Object[0]);

        options.getLogger().log(SentryLevel.DEBUG, "NdkIntegration removed.");
      } catch (NoSuchMethodException e) {
        options
            .getLogger()
            .log(SentryLevel.ERROR, "Failed to invoke the SentryNdk.close method.", e);
      } catch (Throwable e) {
        options.getLogger().log(SentryLevel.ERROR, "Failed to close SentryNdk.", e);
      } finally {
        disableNdkIntegration(this.options);
      }
    }
  }
}
