package io.sentry.android.core;

import io.sentry.EventProcessor;
import io.sentry.protocol.MeasurementValue;
import io.sentry.protocol.SentryTransaction;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PerformanceAndroidEventProcessor implements EventProcessor {

  @Override
  public @NotNull SentryTransaction process(
      @NotNull SentryTransaction transaction, @Nullable Object hint) {
    if (!AppStartState.getInstance().isSentStartMetric()) {
      Long appStartUpInterval = AppStartState.getInstance().getAppStartInterval();
      if (appStartUpInterval != null) {
        MeasurementValue value = new MeasurementValue((float) appStartUpInterval);

        String appStartKey =
            AppStartState.getInstance().getColdStart() ? "app_start_cold" : "app_start_warm";

        transaction.getMeasurements().put(appStartKey, value);
        AppStartState.getInstance().setSentStartUp();
      }
    }

    // this attaches these metrics to all following transactions and its the sum of all frames
    // during apps lifecycle, not specific per screen
    final Map<String, @NotNull MeasurementValue> framesMetrics =
        ActivityFramesState.getInstance().getMetrics();
    if (framesMetrics != null) {
      transaction.getMeasurements().putAll(framesMetrics);
    }

    return transaction;
  }
}
