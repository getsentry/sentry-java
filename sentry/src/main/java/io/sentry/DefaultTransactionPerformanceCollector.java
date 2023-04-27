package io.sentry;

import io.sentry.util.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class DefaultTransactionPerformanceCollector
    implements TransactionPerformanceCollector {
  private static final long TRANSACTION_COLLECTION_INTERVAL_MILLIS = 100;
  private static final long TRANSACTION_COLLECTION_TIMEOUT_MILLIS = 30000;
  private final @NotNull Object timerLock = new Object();
  private volatile @Nullable Timer timer = null;
  private final @NotNull Map<String, List<PerformanceCollectionData>> performanceDataMap =
      new ConcurrentHashMap<>();
  private final @NotNull List<ICollector> collectors;
  private final @NotNull SentryOptions options;
  private final @NotNull AtomicBoolean isStarted = new AtomicBoolean(false);

  public DefaultTransactionPerformanceCollector(final @NotNull SentryOptions options) {
    this.options = Objects.requireNonNull(options, "The options object is required.");
    this.collectors = options.getCollectors();
  }

  @Override
  @SuppressWarnings("FutureReturnValueIgnored")
  public void start(final @NotNull ITransaction transaction) {
    if (collectors.isEmpty()) {
      options
          .getLogger()
          .log(
              SentryLevel.INFO,
              "No collector found. Performance stats will not be captured during transactions.");
      return;
    }

    if (!performanceDataMap.containsKey(transaction.getEventId().toString())) {
      performanceDataMap.put(transaction.getEventId().toString(), new ArrayList<>());
      // We schedule deletion of collected performance data after a timeout
      try {
        options
            .getExecutorService()
            .schedule(() -> stop(transaction), TRANSACTION_COLLECTION_TIMEOUT_MILLIS);
      } catch (Throwable e) {
        options
            .getLogger()
            .log(
                SentryLevel.ERROR,
                "Failed to call the executor. Performance collector will not be automatically finished",
                e);
      }
    }
    if (!isStarted.getAndSet(true)) {
      synchronized (timerLock) {
        if (timer == null) {
          timer = new Timer(true);
        }
        // We schedule the timer to call setup() on collectors immediately in the background.
        timer.schedule(
            new TimerTask() {
              @Override
              public void run() {
                for (ICollector collector : collectors) {
                  collector.setup();
                }
              }
            },
            0L);
        // We schedule the timer to start after a delay, so we let some time pass between setup()
        // and collect() calls.
        // This way ICollectors that collect average stats based on time intervals, like
        // AndroidCpuCollector, can have an actual time interval to evaluate.
        TimerTask timerTask =
            new TimerTask() {
              @Override
              public void run() {
                final @NotNull PerformanceCollectionData tempData = new PerformanceCollectionData();

                for (ICollector collector : collectors) {
                  collector.collect(tempData);
                }

                for (List<PerformanceCollectionData> data : performanceDataMap.values()) {
                  data.add(tempData);
                }
              }
            };
        timer.scheduleAtFixedRate(
            timerTask,
            TRANSACTION_COLLECTION_INTERVAL_MILLIS,
            TRANSACTION_COLLECTION_INTERVAL_MILLIS);
      }
    }
  }

  @Override
  public @Nullable List<PerformanceCollectionData> stop(final @NotNull ITransaction transaction) {
    List<PerformanceCollectionData> data =
        performanceDataMap.remove(transaction.getEventId().toString());
    options
        .getLogger()
        .log(
            SentryLevel.DEBUG,
            "stop collecting performance info for transactions %s (%s)",
            transaction.getName(),
            transaction.getSpanContext().getTraceId().toString());
    if (performanceDataMap.isEmpty() && isStarted.getAndSet(false)) {
      synchronized (timerLock) {
        if (timer != null) {
          timer.cancel();
          timer = null;
        }
      }
    }
    return data;
  }

  @Override
  public void close() {
    performanceDataMap.clear();
    options
        .getLogger()
        .log(SentryLevel.DEBUG, "stop collecting all performance info for transactions");
    if (isStarted.getAndSet(false)) {
      synchronized (timerLock) {
        if (timer != null) {
          timer.cancel();
          timer = null;
        }
      }
    }
  }
}
