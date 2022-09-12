package io.sentry.measurement;

import io.sentry.ITransaction;
import io.sentry.protocol.MeasurementValue;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public interface MeasurementCollector {
  void onTransactionStarted(@NotNull ITransaction transaction);

  @Nullable
  Map<String, MeasurementValue> onTransactionFinished(
      @NotNull ITransaction transaction, @NotNull MeasurementContext context);
}
