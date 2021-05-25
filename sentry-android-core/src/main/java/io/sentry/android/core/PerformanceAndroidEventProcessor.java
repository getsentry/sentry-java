package io.sentry.android.core;

import io.sentry.EventProcessor;
import io.sentry.protocol.MeasurementValue;
import io.sentry.protocol.SentryTransaction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Event Processor responsable for adding Android metrics to transactions */
final class PerformanceAndroidEventProcessor implements EventProcessor {

  final @NotNull SentryAndroidOptions options;

  PerformanceAndroidEventProcessor(final @NotNull SentryAndroidOptions options) {
    this.options = options;
  }

  // transactions may be started in parallel, making this field volatile instead of a lock
  // to avoid contention.
  private volatile boolean sentStartMeasurement = false;

  @Override
  public @NotNull SentryTransaction process(
      @NotNull SentryTransaction transaction, @Nullable Object hint) {
    // the app start metric is sent only once when the 1st transaction happens
    // after the app start is collected.
    if (!sentStartMeasurement && options.isTracingEnabled()) {
      Long appStartUpInterval = AppStartState.getInstance().getAppStartInterval();
      // if appStartUpInterval is null, metrics are not ready to be sent
      if (appStartUpInterval != null) {
        MeasurementValue value = new MeasurementValue((float) appStartUpInterval);

        String appStartKey =
            AppStartState.getInstance().getColdStart() ? "app_start_cold" : "app_start_warm";

        transaction.getMeasurements().put(appStartKey, value);
        sentStartMeasurement = true;
      }
    }

    return transaction;
  }
}
