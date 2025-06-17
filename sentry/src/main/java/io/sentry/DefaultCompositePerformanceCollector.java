package io.sentry;

import io.sentry.util.AutoClosableReentrantLock;
import io.sentry.util.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class DefaultCompositePerformanceCollector implements CompositePerformanceCollector {
  private static final long TRANSACTION_COLLECTION_INTERVAL_MILLIS = 100;
  private static final long TRANSACTION_COLLECTION_TIMEOUT_MILLIS = 30000;
  private final @NotNull AutoClosableReentrantLock timerLock = new AutoClosableReentrantLock();
  private volatile @Nullable Timer timer = null;
  private final @NotNull Map<String, List<PerformanceCollectionData>> performanceDataMap =
      new ConcurrentHashMap<>();
  private final @NotNull List<IPerformanceSnapshotCollector> snapshotCollectors;
  private final @NotNull List<IPerformanceContinuousCollector> continuousCollectors;
  private final boolean hasNoCollectors;

  private final @NotNull SentryOptions options;
  private final @NotNull AtomicBoolean isStarted = new AtomicBoolean(false);
  private long lastCollectionTimestamp = 0;

  public DefaultCompositePerformanceCollector(final @NotNull SentryOptions options) {
    this.options = Objects.requireNonNull(options, "The options object is required.");
    this.snapshotCollectors = new ArrayList<>();
    this.continuousCollectors = new ArrayList<>();

    final @NotNull List<IPerformanceCollector> performanceCollectors =
        options.getPerformanceCollectors();
    for (IPerformanceCollector performanceCollector : performanceCollectors) {
      if (performanceCollector instanceof IPerformanceSnapshotCollector) {
        snapshotCollectors.add((IPerformanceSnapshotCollector) performanceCollector);
      }
      if (performanceCollector instanceof IPerformanceContinuousCollector) {
        continuousCollectors.add((IPerformanceContinuousCollector) performanceCollector);
      }
    }

    hasNoCollectors = snapshotCollectors.isEmpty() && continuousCollectors.isEmpty();
  }

  @Override
  @SuppressWarnings("FutureReturnValueIgnored")
  public void start(final @NotNull ITransaction transaction) {
    if (hasNoCollectors) {
      options
          .getLogger()
          .log(
              SentryLevel.INFO,
              "No collector found. Performance stats will not be captured during transactions.");
      return;
    }

    for (final @NotNull IPerformanceContinuousCollector collector : continuousCollectors) {
      collector.onSpanStarted(transaction);
    }

    if (!performanceDataMap.containsKey(transaction.getEventId().toString())) {
      performanceDataMap.put(transaction.getEventId().toString(), new ArrayList<>());
      // We schedule deletion of collected performance data after a timeout
      try {
        options
            .getExecutorService()
            .schedule(() -> stop(transaction), TRANSACTION_COLLECTION_TIMEOUT_MILLIS);
      } catch (RejectedExecutionException e) {
        options
            .getLogger()
            .log(
                SentryLevel.ERROR,
                "Failed to call the executor. Performance collector will not be automatically finished. Did you call Sentry.close()?",
                e);
      }
    }
    start(transaction.getEventId().toString());
  }

  @Override
  public void start(final @NotNull String id) {
    if (hasNoCollectors) {
      options
          .getLogger()
          .log(
              SentryLevel.INFO,
              "No collector found. Performance stats will not be captured during transactions.");
      return;
    }

    if (!performanceDataMap.containsKey(id)) {
      performanceDataMap.put(id, new ArrayList<>());
    }
    if (!isStarted.getAndSet(true)) {
      try (final @NotNull ISentryLifecycleToken ignored = timerLock.acquire()) {
        if (timer == null) {
          timer = new Timer(true);
        }
        // We schedule the timer to call setup() on collectors immediately in the background.
        timer.schedule(
            new TimerTask() {
              @Override
              public void run() {
                for (IPerformanceSnapshotCollector collector : snapshotCollectors) {
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
                long now = System.currentTimeMillis();
                // The timer is scheduled to run every 100ms on average. In case it takes longer,
                // subsequent tasks are executed more quickly. If two tasks are scheduled to run in
                // less than 10ms, the measurement that we collect is not meaningful, so we skip it
                if (now - lastCollectionTimestamp <= 10) {
                  return;
                }
                lastCollectionTimestamp = now;
                final @NotNull PerformanceCollectionData tempData = new PerformanceCollectionData();

                for (IPerformanceSnapshotCollector collector : snapshotCollectors) {
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
  public void onSpanStarted(@NotNull ISpan span) {
    for (final @NotNull IPerformanceContinuousCollector collector : continuousCollectors) {
      collector.onSpanStarted(span);
    }
  }

  @Override
  public void onSpanFinished(@NotNull ISpan span) {
    for (final @NotNull IPerformanceContinuousCollector collector : continuousCollectors) {
      collector.onSpanFinished(span);
    }
  }

  @Override
  public @Nullable List<PerformanceCollectionData> stop(final @NotNull ITransaction transaction) {
    options
        .getLogger()
        .log(
            SentryLevel.DEBUG,
            "stop collecting performance info for transactions %s (%s)",
            transaction.getName(),
            transaction.getSpanContext().getTraceId().toString());

    for (final @NotNull IPerformanceContinuousCollector collector : continuousCollectors) {
      collector.onSpanFinished(transaction);
    }

    return stop(transaction.getEventId().toString());
  }

  @Override
  public @Nullable List<PerformanceCollectionData> stop(final @NotNull String id) {
    final @Nullable List<PerformanceCollectionData> data = performanceDataMap.remove(id);

    // close if they are no more running requests
    if (performanceDataMap.isEmpty()) {
      close();
    }
    return data;
  }

  @Override
  public void close() {
    options
        .getLogger()
        .log(SentryLevel.DEBUG, "stop collecting all performance info for transactions");

    performanceDataMap.clear();
    for (final @NotNull IPerformanceContinuousCollector collector : continuousCollectors) {
      collector.clear();
    }
    if (isStarted.getAndSet(false)) {
      try (final @NotNull ISentryLifecycleToken ignored = timerLock.acquire()) {
        if (timer != null) {
          timer.cancel();
          timer = null;
        }
      }
    }
  }
}
