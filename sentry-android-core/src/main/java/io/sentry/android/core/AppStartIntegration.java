package io.sentry.android.core;

import static io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion;

import io.sentry.IScopes;
import io.sentry.Integration;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.protocol.SentryId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Registers standalone {@code App Start} tracing when {@link
 * SentryAndroidOptions#isEnableStandaloneAppStartTracing()} is enabled.
 *
 * <p>Delegates to {@link StandaloneAppStartReporter}, which registers as {@link
 * FirstUiLoadListener} for callbacks from {@link ActivityLifecycleIntegration}.
 */
final class AppStartIntegration implements Integration, java.io.Closeable {

  private final @NotNull FirstUiLoadCoordinator firstUiLoadCoordinator;
  private @Nullable StandaloneAppStartReporter reporter;

  AppStartIntegration(final @NotNull FirstUiLoadCoordinator firstUiLoadCoordinator) {
    this.firstUiLoadCoordinator = firstUiLoadCoordinator;
  }

  @Override
  public void register(final @NotNull IScopes scopes, final @NotNull SentryOptions options) {
    final @Nullable SentryAndroidOptions androidOptions =
        (options instanceof SentryAndroidOptions) ? (SentryAndroidOptions) options : null;
    if (androidOptions == null || !isEnabled(androidOptions)) {
      return;
    }

    reporter = new StandaloneAppStartReporter(scopes, "auto.ui.activity", firstUiLoadCoordinator);
    reporter.register();

    androidOptions.getLogger().log(SentryLevel.DEBUG, "AppStartIntegration installed.");
    addIntegrationToSdkVersion("AppStart");
  }

  @VisibleForTesting
  static boolean isEnabled(final @NotNull SentryAndroidOptions options) {
    return options.isTracingEnabled()
        && options.isEnableAutoActivityLifecycleTracing()
        && options.isEnableStandaloneAppStartTracing();
  }

  @Override
  public void close() {
    if (reporter != null) {
      reporter.close();
      reporter = null;
    }
  }

  @TestOnly
  @Nullable
  StandaloneAppStartReporter getReporter() {
    return reporter;
  }

  @TestOnly
  @Nullable
  SentryId getReusableTraceId() {
    return reporter != null ? reporter.getReusableTraceId() : null;
  }

  @TestOnly
  void setReusableTraceId(final @Nullable SentryId traceId) {
    if (reporter != null) {
      reporter.setReusableTraceId(traceId);
    }
  }
}
