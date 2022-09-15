package io.sentry.measurement;

import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class MeasurementBackgroundWorker implements Runnable {

  private final @NotNull SentryOptions options;
  //  private final @NotNull Map<MeasurementBackgroundServiceType, AtomicInteger> listenCounts;
  private final @NotNull Map<MeasurementBackgroundServiceType, MeasurementBackgroundCollector>
      backgroundCollectors;
  private final Map<MeasurementBackgroundServiceType, TimeSeriesStorage<Object>> storage;
  private final int pollingInterval;
  private final @NotNull AtomicBoolean isRunning = new AtomicBoolean(false);

  public MeasurementBackgroundWorker(
      @NotNull SentryOptions options,
      @NotNull Map<MeasurementBackgroundServiceType, AtomicInteger> listenCounts,
      @NotNull
          Map<MeasurementBackgroundServiceType, MeasurementBackgroundCollector>
              backgroundCollectors,
      @NotNull Map<MeasurementBackgroundServiceType, TimeSeriesStorage<Object>> storage,
      int pollingInterval) {
    this.options = options;
    //    this.listenCounts = listenCounts;
    this.backgroundCollectors = backgroundCollectors;
    this.storage = storage;
    this.pollingInterval = pollingInterval;
  }

  // bound memory usage with tracked data

  @Override
  public void run() {
    if (isRunning.get()) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Not running MeasurementBackgroundWorker as the previous run has not finished yet.");
      return;
    }
    options.getLogger().log(SentryLevel.DEBUG, "Running MeasurementBackgroundWorker.");

    isRunning.set(true);

    long startTime = System.currentTimeMillis();

    try {
      for (MeasurementBackgroundServiceType measurementType :
          MeasurementBackgroundServiceType.values()) {
        //        AtomicInteger listenCount = listenCounts.get(measurementType);
        //        if (listenCount != null && listenCount.get() > 0) {
        //          options
        //              .getLogger()
        //              .log(
        //                  SentryLevel.INFO,
        //                  "Performing background work for %s as there are %d listeners [%s]",
        //                  measurementType,
        //                  listenCount.get(),
        //                  Thread.currentThread().getName());
        //        long startTimeSingle = System.currentTimeMillis();
        doWorkFor(measurementType);
        //        long deltaSingle = System.currentTimeMillis() - startTimeSingle;
        //        options.getLogger().log(SentryLevel.DEBUG, "Background measurement collectioin for
        // %s took %d ms", measurementType, deltaSingle);
        //        }
      }
    } catch (Exception e) {
      options
          .getLogger()
          .log(
              SentryLevel.ERROR,
              "MeasurementBackgroundWorker Error while doing background work for measurements.",
              e);
    } finally {
      isRunning.set(false);
      long timeDelta = System.currentTimeMillis() - startTime;
      double warningThreshold = pollingInterval * 0.9;
      //      options.getLogger().log(SentryLevel.DEBUG, "MeasurementBackgroundWorker collection for
      // all took %d ms", timeDelta);
      if (timeDelta > warningThreshold) {
        options
            .getLogger()
            .log(
                SentryLevel.WARNING,
                "Measurement collection in background is taking > 90 %% of %d",
                pollingInterval);
      }
    }
  }

  private void doWorkFor(@NotNull MeasurementBackgroundServiceType measurementType) {
    MeasurementBackgroundCollector backgroundCollector = backgroundCollectors.get(measurementType);
    if (backgroundCollector != null) {
      @Nullable Object result = backgroundCollector.collect();
      if (result != null) {
        // TODO maybe cleanup old entries (time wise) so the queue doesn't stay full all the time
        @Nullable TimeSeriesStorage<Object> storageQueue = storage.get(measurementType);
        if (storageQueue != null) {
          //                    options
          //                      .getLogger()
          //                      .log(SentryLevel.INFO, "MeasurementBackgroundWorker storage for %s
          // has size %d", measurementType,
          //           storageQueue.size());
          storageQueue.add(result);
        }
      }
    }
  }
}
