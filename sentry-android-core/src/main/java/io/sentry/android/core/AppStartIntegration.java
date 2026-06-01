package io.sentry.android.core;

import static io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion;

import io.sentry.IScopes;
import io.sentry.ISpan;
import io.sentry.ITransaction;
import io.sentry.Integration;
import io.sentry.SentryDate;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.TracesSamplingDecision;
import io.sentry.protocol.SentryId;
import io.sentry.util.Objects;
import java.io.Closeable;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Registers standalone {@code App Start} tracing when {@link
 * SentryAndroidOptions#isEnableStandaloneAppStartTracing()} is enabled.
 *
 * <p>Delegates transaction work to {@link StandaloneAppStartReporter} and exposes a {@link
 * StandaloneAppStartCoordinator} for {@link ActivityLifecycleIntegration}.
 */
public final class AppStartIntegration
    implements Integration, Closeable, StandaloneAppStartCoordinator {

  private static final String TRACE_ORIGIN = "auto.ui.activity";

  private @Nullable StandaloneAppStartReporter reporter;

  public AppStartIntegration() {}

  @Override
  public void register(final @NotNull IScopes scopes, final @NotNull SentryOptions options) {
    final @Nullable SentryAndroidOptions androidOptions =
        (options instanceof SentryAndroidOptions) ? (SentryAndroidOptions) options : null;
    if (androidOptions == null || !isEnabled(androidOptions)) {
      return;
    }

    reporter = new StandaloneAppStartReporter(scopes, TRACE_ORIGIN);
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
  public void close() throws IOException {
    if (reporter != null) {
      reporter.close();
      reporter = null;
    }
  }

  @Override
  public @Nullable SentryId getReusableTraceId() {
    return reporter != null ? reporter.getReusableTraceId() : null;
  }

  @Override
  public void setReusableTraceId(final @Nullable SentryId traceId) {
    if (reporter != null) {
      reporter.setReusableTraceId(traceId);
    }
  }

  @Override
  public @NotNull ITransaction startForActivity(
      final @NotNull SentryId traceId,
      final @NotNull String activityName,
      final @NotNull SentryDate appStartTime,
      final @Nullable TracesSamplingDecision samplingDecision) {
    return Objects.requireNonNull(reporter, "AppStartIntegration is not registered")
        .startForActivity(traceId, activityName, appStartTime, samplingDecision);
  }

  @Override
  public @Nullable ISpan getAppStartTransaction() {
    return reporter != null ? reporter.getAppStartTransaction() : null;
  }

  @Override
  public void finishAppStart(final @NotNull SentryDate endDate) {
    if (reporter != null) {
      reporter.finishAppStart(endDate);
    }
  }

  @Override
  public void onActivityDestroyed() {
    if (reporter != null) {
      reporter.onActivityDestroyed();
    }
  }

  @TestOnly
  @Nullable
  StandaloneAppStartReporter getReporter() {
    return reporter;
  }
}
