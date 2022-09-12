package io.sentry.measurement;

import io.sentry.DateUtils;
import io.sentry.ITransaction;
import io.sentry.protocol.MeasurementValue;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public abstract class BackgroundAwareMeasurementCollector implements MeasurementCollector {

  protected final @NotNull MeasurementBackgroundService backgroundService;
  protected @Nullable Date startDate;

  public BackgroundAwareMeasurementCollector(
      @NotNull MeasurementBackgroundService backgroundService) {
    this.backgroundService = backgroundService;
  }

  protected abstract List<MeasurementBackgroundServiceType> listenToTypes();

  @Override
  public void onTransactionStarted(@NotNull ITransaction transaction) {
    startDate = DateUtils.getCurrentDateTime();
    List<MeasurementBackgroundServiceType> infos = listenToTypes();
    for (MeasurementBackgroundServiceType info : infos) {
      backgroundService.startPolling(info);
    }

    onTransactionStartedInternal(transaction);
  }

  protected abstract void onTransactionStartedInternal(@NotNull ITransaction transaction);

  @Override
  public @Nullable Map<String, MeasurementValue> onTransactionFinished(
      @NotNull ITransaction transaction, @NotNull MeasurementContext context) {
    try {
      return onTransactionFinishedInternal(transaction, context);
    } finally {
      // TODO should also be called in case finish is never called
      List<MeasurementBackgroundServiceType> infos = listenToTypes();
      for (MeasurementBackgroundServiceType info : infos) {
        backgroundService.stopPolling(info);
      }
    }
  }

  protected abstract @Nullable Map<String, MeasurementValue> onTransactionFinishedInternal(
      @NotNull ITransaction transaction, @NotNull MeasurementContext context);
}
