package io.sentry.android.core.internal.measurement.fps;

import io.sentry.ITransaction;
import io.sentry.measurement.MeasurementCollector;
import io.sentry.measurement.MeasurementContext;
import io.sentry.protocol.MeasurementValue;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class FpsMeasurementCollector implements MeasurementCollector {
  @Override
  public void onTransactionStarted(@NotNull ITransaction transaction) {}

  @Override
  public @Nullable Map<String, MeasurementValue> onTransactionFinished(
      @NotNull ITransaction transaction, @NotNull MeasurementContext context) {
    @NotNull Map<String, MeasurementValue> results = new HashMap<>();

    return results;
  }
}
