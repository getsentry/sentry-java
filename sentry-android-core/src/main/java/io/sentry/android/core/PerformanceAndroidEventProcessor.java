package io.sentry.android.core;

import io.sentry.EventProcessor;
import io.sentry.protocol.MeasurementValue;
import io.sentry.protocol.SentryTransaction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Event Processor responsible for adding Android metrics to transactions */
final class PerformanceAndroidEventProcessor implements EventProcessor {

  private final boolean tracingEnabled;

  private boolean sentStartMeasurement = false;

  // transactions may be started in parallel, locking it so sentStartMeasurement
  private final @NotNull Object measurementLock = new Object();

  PerformanceAndroidEventProcessor(final @NotNull SentryAndroidOptions options) {
    tracingEnabled = options.isTracingEnabled();
  }

  @Override
  public @NotNull SentryTransaction process(
      @NotNull SentryTransaction transaction, @Nullable Object hint) {
    // the app start metric is sent only once when the 1st transaction happens
    // after the app start is collected.
    synchronized (measurementLock) {
      if (!sentStartMeasurement && tracingEnabled) {
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
    }

    return transaction;
  }
}
