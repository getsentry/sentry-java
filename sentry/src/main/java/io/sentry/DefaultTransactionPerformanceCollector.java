package io.sentry;

import io.sentry.util.Objects;
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
  private final @NotNull Map<String, PerformanceCollectionData> performanceDataMap =
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
      performanceDataMap.put(transaction.getEventId().toString(), new PerformanceCollectionData());
      options
          .getExecutorService()
          .schedule(
              () -> {
                PerformanceCollectionData data = stop(transaction);
                if (data != null) {
                  performanceDataMap.put(transaction.getEventId().toString(), data);
                }
              },
              TRANSACTION_COLLECTION_TIMEOUT_MILLIS);
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
        timer.scheduleAtFixedRate(
            new TimerTask() {
              @Override
              public void run() {
                synchronized (timerLock) {
                  for (ICollector collector : collectors) {
                    collector.collect(performanceDataMap.values());
                  }
                  // We commit data after calling all collectors.
                  // This way we avoid issues caused by having multiple cpu or memory collectors.
                  for (PerformanceCollectionData data : performanceDataMap.values()) {
                    data.commitData();
                  }
                }
              }
            },
            TRANSACTION_COLLECTION_INTERVAL_MILLIS,
            TRANSACTION_COLLECTION_INTERVAL_MILLIS);
      }
    }
  }

  @Override
  public @Nullable PerformanceCollectionData stop(final @NotNull ITransaction transaction) {
    PerformanceCollectionData data =
      performanceDataMap.remove(transaction.getEventId().toString());
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
}
