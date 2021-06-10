package io.sentry.android.core;

import static io.sentry.android.core.ActivityLifecycleIntegration.APP_START_OP;

import io.sentry.EventProcessor;
import io.sentry.protocol.MeasurementValue;
import io.sentry.protocol.SentrySpan;
import io.sentry.protocol.SentryTransaction;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Event Processor responsible for adding Android metrics to transactions */
final class PerformanceAndroidEventProcessor implements EventProcessor {

  private final boolean tracingEnabled;

  private boolean sentStartMeasurement = false;

  PerformanceAndroidEventProcessor(final @NotNull SentryAndroidOptions options) {
    tracingEnabled = options.isTracingEnabled();
  }

  @Override
  public synchronized @NotNull SentryTransaction process(
      @NotNull SentryTransaction transaction, @Nullable Object hint) {
    // the app start metric is sent only once when the 1st transaction happens
    // after the app start is collected.
    if (!sentStartMeasurement && tracingEnabled && hasAppStartSpan(transaction.getSpans())) {
      final Long appStartUpInterval = AppStartState.getInstance().getAppStartInterval();
      // if appStartUpInterval is null, metrics are not ready to be sent
      if (appStartUpInterval != null) {
        final MeasurementValue value = new MeasurementValue((float) appStartUpInterval);

        final String appStartKey =
            AppStartState.getInstance().isColdStart() ? "app_start_cold" : "app_start_warm";

        transaction.getMeasurements().put(appStartKey, value);
        sentStartMeasurement = true;
      }
    }

    return transaction;
  }

  private boolean hasAppStartSpan(final @NotNull List<SentrySpan> spans) {
    for (final SentrySpan span : spans) {
      if (span.getOp().contentEquals(APP_START_OP)) {
        return true;
      }
    }
    return false;
  }
}
