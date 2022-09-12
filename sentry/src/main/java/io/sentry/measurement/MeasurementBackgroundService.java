package io.sentry.measurement;

import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class MeasurementBackgroundService {

  private final @NotNull Map<MeasurementBackgroundServiceType, AtomicInteger> listenCounts =
      new HashMap<>();
  private final @NotNull ScheduledExecutorService executorService =
      Executors.newSingleThreadScheduledExecutor();
  private final @NotNull SentryOptions options;
  private final @NotNull Map<MeasurementBackgroundServiceType, MeasurementBackgroundCollector>
      backgroundCollectors = new HashMap<>();
  private final @NotNull Map<MeasurementBackgroundServiceType, TimeSeriesStorage<Object>> storage =
      new HashMap<>();
  private final int pollingInterval;

  public MeasurementBackgroundService(@NotNull SentryOptions options) {
    this.options = options;
    this.pollingInterval = 50;
    for (MeasurementBackgroundServiceType measurementType :
        MeasurementBackgroundServiceType.values()) {
      listenCounts.put(measurementType, new AtomicInteger(0));
      // TODO capacity option
      storage.put(measurementType, new TimeSeriesStorage<>(100));
    }
  }

  public int getPollingInterval() {
    return pollingInterval;
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  public void start() {
    executorService.scheduleAtFixedRate(
        new MeasurementBackgroundWorker(
            options, listenCounts, backgroundCollectors, storage, pollingInterval),
        0,
        pollingInterval,
        TimeUnit.MILLISECONDS);
  }

  // TODO call
  public void stop() {
    executorService.shutdown();
  }

  public void startPolling(@NotNull MeasurementBackgroundServiceType measurementType) {
    final @Nullable AtomicInteger listenCount = listenCounts.get(measurementType);
    if (listenCount != null) {
      listenCount.getAndIncrement();
    }
  }

  public void stopPolling(@NotNull MeasurementBackgroundServiceType measurementType) {
    final @Nullable AtomicInteger listenCount = listenCounts.get(measurementType);
    if (listenCount != null) {
      listenCount.decrementAndGet();
    }
  }

  public @Nullable Object getLatest(@NotNull MeasurementBackgroundServiceType measurementType) {
    final @Nullable TimeSeriesStorage<Object> timeSeriesStorage = storage.get(measurementType);
    if (timeSeriesStorage != null) {
      return timeSeriesStorage.getLatest(pollingInterval);
    }

    return null;
  }

  public @NotNull List<Object> getFrom(
      @NotNull MeasurementBackgroundServiceType measurementType,
      @Nullable Date minDate,
      @Nullable Integer allowedTimeBeforeMin) {
    final @Nullable TimeSeriesStorage<Object> timieSeriesStorage = storage.get(measurementType);
    final int allowedTimeBeforeOrZero = allowedTimeBeforeMin != null ? allowedTimeBeforeMin : 0;
    if (minDate != null && timieSeriesStorage != null) {
      return timieSeriesStorage.getFrom(minDate, allowedTimeBeforeOrZero);
    }

    return new ArrayList<>();
  }

  public void registerBackgroundCollectors(
      List<MeasurementCollectorFactory> measurementCollectorFactories) {
    for (MeasurementCollectorFactory factory : measurementCollectorFactories) {
      final @Nullable MeasurementBackgroundCollector backgroundCollector =
          factory.createBackgroundCollector(options);
      if (backgroundCollector != null) {
        options
            .getLogger()
            .log(
                SentryLevel.DEBUG,
                "Registering background measurement collector for %s.",
                backgroundCollector.getMeasurementType());
        backgroundCollectors.put(backgroundCollector.getMeasurementType(), backgroundCollector);
      }
    }
  }
}
