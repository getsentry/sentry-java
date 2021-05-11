package io.sentry.android.core;

import io.sentry.EventProcessor;
import io.sentry.protocol.MeasurementValue;
import io.sentry.protocol.SentryTransaction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PerformanceAndroidEventProcessor implements EventProcessor {

  @Override
  public @Nullable SentryTransaction process(
      @NotNull SentryTransaction transaction, @Nullable Object hint) {
    if (!AppStartUpState.getInstance().isSentStartUp()) {
      Long appStartUpInterval = AppStartUpState.getInstance().getAppStartInterval();
      if (appStartUpInterval != null) {
        MeasurementValue value = new MeasurementValue((float) appStartUpInterval);

        String appStartKey =
            AppStartUpState.getInstance().isColdStartUp() ? "cold_start_time" : "warm_start_time";

        transaction.getMeasurements().put(appStartKey, value);
        AppStartUpState.getInstance().setSentStartUp();
      }
    }

    return transaction;
  }
}
