package io.sentry;

import io.sentry.util.AutoClosableReentrantLock;
import io.sentry.util.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class DefaultCompositePerformanceCollector implements CompositePerformanceCollector {
  private static final long TRANSACTION_COLLECTION_INTERVAL_MILLIS = 100;
  private static final long TRANSACTION_COLLECTION_TIMEOUT_MILLIS = 30000;
  private final @NotNull AutoClosableReentrantLock timerLock = new AutoClosableReentrantLock();
  private volatile @Nullable Future<?> collectFuture = null;

  /**
   * Incremented on close() so an in-flight collect run doesn't reschedule itself after its chain
   * was cancelled. Guarded by timerLock.
   */
  private long generation = 0;

  private final @NotNull Map<String, CompositeData> compositeDataMap = new ConcurrentHashMap<>();
  private final @NotNull List<IPerformanceSnapshotCollector> snapshotCollectors;
  private final @NotNull List<IPerformanceContinuousCollector> continuousCollectors;
  private final boolean hasNoCollectors;

  private final @NotNull SentryOptions options;
  private final @NotNull AtomicBoolean isStarted = new AtomicBoolean(false);

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

    final @NotNull String id = transaction.getEventId().toString();
    if (!compositeDataMap.containsKey(id)) {
      compositeDataMap.put(id, new CompositeData(transaction));
    }
    start(id);
  }

  @Override
  @SuppressWarnings("FutureReturnValueIgnored")
  public void start(final @NotNull String id) {
    if (hasNoCollectors) {
      options
          .getLogger()
          .log(
              SentryLevel.INFO,
              "No collector found. Performance stats will not be captured during transactions.");
      return;
    }

    if (!compositeDataMap.containsKey(id)) {
      // Transactions are added in start(ITransaction). If we are here, it means we don't come from
      // a transaction
      compositeDataMap.put(id, new CompositeData(null));
    }
    if (!isStarted.getAndSet(true)) {
      try (final @NotNull ISentryLifecycleToken ignored = timerLock.acquire()) {
        final long currentGeneration = generation;
        try {
          // We schedule the executor to call setup() on collectors immediately in the background.
          options
              .getTimerExecutorService()
              .schedule(
                  () -> {
                    for (IPerformanceSnapshotCollector collector : snapshotCollectors) {
                      collector.setup();
                    }
                  },
                  0L);
          // We schedule the collection to start after a delay, so we let some time pass between
          // setup() and collect() calls.
          // This way ICollectors that collect average stats based on time intervals, like
          // AndroidCpuCollector, can have an actual time interval to evaluate.
          collectFuture =
              options
                  .getTimerExecutorService()
                  .schedule(
                      () -> collectAndReschedule(currentGeneration),
                      TRANSACTION_COLLECTION_INTERVAL_MILLIS);
        } catch (Throwable t) {
          options
              .getLogger()
              .log(SentryLevel.WARNING, "Failed to schedule performance collection.", t);
        }
      }
    }
  }

  private void collectAndReschedule(final long scheduledGeneration) {
    final @NotNull PerformanceCollectionData tempData =
        new PerformanceCollectionData(options.getDateProvider().now().nanoTimestamp());

    // Enrich tempData using collectors
    for (IPerformanceSnapshotCollector collector : snapshotCollectors) {
      collector.collect(tempData);
    }

    // Add the enriched tempData to all transactions/profiles/objects that collect data.
    // Then Check if that object timed out.
    final @NotNull List<ITransaction> timedOutTransactions = new ArrayList<>();
    for (CompositeData data : compositeDataMap.values()) {
      if (data.addDataAndCheckTimeout(tempData)) {
        // timed out
        if (data.transaction != null) {
          timedOutTransactions.add(data.transaction);
        }
      }
    }
    // Stop timed out transactions outside compositeDataMap loop, as stop() modifies the map
    for (final @NotNull ITransaction t : timedOutTransactions) {
      stop(t);
    }

    try (final @NotNull ISentryLifecycleToken ignored = timerLock.acquire()) {
      // stopping a timed out transaction above may have closed this collector; only reschedule if
      // this run still belongs to the current collection chain
      if (scheduledGeneration == generation) {
        try {
          collectFuture =
              options
                  .getTimerExecutorService()
                  .schedule(
                      () -> collectAndReschedule(scheduledGeneration),
                      TRANSACTION_COLLECTION_INTERVAL_MILLIS);
        } catch (Throwable t) {
          options
              .getLogger()
              .log(SentryLevel.WARNING, "Failed to reschedule performance collection.", t);
        }
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
    final @Nullable CompositeData data = compositeDataMap.remove(id);
    options.getLogger().log(SentryLevel.DEBUG, "stop collecting performance info for " + id);

    // close if there are no more running requests
    if (compositeDataMap.isEmpty()) {
      close();
    }
    return data != null ? data.dataList : null;
  }

  @Override
  public void close() {
    options
        .getLogger()
        .log(SentryLevel.DEBUG, "stop collecting all performance info for transactions");

    compositeDataMap.clear();
    for (final @NotNull IPerformanceContinuousCollector collector : continuousCollectors) {
      collector.clear();
    }
    if (isStarted.getAndSet(false)) {
      try (final @NotNull ISentryLifecycleToken ignored = timerLock.acquire()) {
        generation++;
        if (collectFuture != null) {
          collectFuture.cancel(false);
          collectFuture = null;
        }
      }
    }
  }

  private class CompositeData {
    private final @NotNull List<PerformanceCollectionData> dataList;
    private final @Nullable ITransaction transaction;
    private final long startTimestamp;

    private CompositeData(final @Nullable ITransaction transaction) {
      this.dataList = new ArrayList<>();
      this.transaction = transaction;
      this.startTimestamp = options.getDateProvider().now().nanoTimestamp();
    }

    /**
     * Adds the data to the internal list of PerformanceCollectionData. Then it checks if data
     * collection timed out (for transactions only).
     *
     * @return true if data collection timed out (for transactions only).
     */
    boolean addDataAndCheckTimeout(final @NotNull PerformanceCollectionData data) {
      dataList.add(data);
      return transaction != null
          && options.getDateProvider().now().nanoTimestamp()
              > startTimestamp
                  + TimeUnit.MILLISECONDS.toNanos(TRANSACTION_COLLECTION_TIMEOUT_MILLIS);
    }
  }
}
