package io.sentry.metrics;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.DataCategory;
import io.sentry.ISentryClient;
import io.sentry.ISentryExecutorService;
import io.sentry.ISentryLifecycleToken;
import io.sentry.SentryExecutorService;
import io.sentry.SentryLevel;
import io.sentry.SentryMetricsEvent;
import io.sentry.SentryMetricsEvents;
import io.sentry.SentryOptions;
import io.sentry.clientreport.DiscardReason;
import io.sentry.transport.ReusableCountLatch;
import io.sentry.util.AutoClosableReentrantLock;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Open
public class MetricsBatchProcessor implements IMetricsBatchProcessor {

  public static final int FLUSH_AFTER_MS = 5000;
  public static final int MAX_BATCH_SIZE = 1000;
  public static final int MAX_QUEUE_SIZE = 10000;

  protected final @NotNull SentryOptions options;
  private final @NotNull ISentryClient client;
  private final @NotNull Queue<SentryMetricsEvent> queue;
  private final @NotNull ISentryExecutorService executorService;
  private volatile @Nullable Future<?> scheduledFlush;
  private static final @NotNull AutoClosableReentrantLock scheduleLock =
      new AutoClosableReentrantLock();
  private volatile boolean hasScheduled = false;

  private final @NotNull ReusableCountLatch pendingCount = new ReusableCountLatch();

  public MetricsBatchProcessor(
      final @NotNull SentryOptions options, final @NotNull ISentryClient client) {
    this.options = options;
    this.client = client;
    this.queue = new ConcurrentLinkedQueue<>();
    this.executorService = new SentryExecutorService(options);
  }

  @Override
  public void add(final @NotNull SentryMetricsEvent metricsEvent) {
    if (pendingCount.getCount() >= MAX_QUEUE_SIZE) {
      options
          .getClientReportRecorder()
          .recordLostEvent(DiscardReason.QUEUE_OVERFLOW, DataCategory.TraceMetric);
      return;
    }
    pendingCount.increment();
    queue.offer(metricsEvent);
    maybeSchedule(false, false);
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  @Override
  public void close(final boolean isRestarting) {
    if (isRestarting) {
      maybeSchedule(true, true);
      executorService.submit(() -> executorService.close(options.getShutdownTimeoutMillis()));
    } else {
      executorService.close(options.getShutdownTimeoutMillis());
      while (!queue.isEmpty()) {
        flushBatch();
      }
    }
  }

  private void maybeSchedule(boolean forceSchedule, boolean immediately) {
    if (hasScheduled && !forceSchedule) {
      return;
    }
    try (final @NotNull ISentryLifecycleToken ignored = scheduleLock.acquire()) {
      final @Nullable Future<?> latestScheduledFlush = scheduledFlush;
      if (forceSchedule
          || latestScheduledFlush == null
          || latestScheduledFlush.isDone()
          || latestScheduledFlush.isCancelled()) {
        hasScheduled = true;
        final int flushAfterMs = immediately ? 0 : FLUSH_AFTER_MS;
        try {
          scheduledFlush = executorService.schedule(new BatchRunnable(), flushAfterMs);
        } catch (RejectedExecutionException e) {
          hasScheduled = false;
          options
              .getLogger()
              .log(SentryLevel.WARNING, "Metrics batch processor flush task rejected", e);
        }
      }
    }
  }

  @Override
  public void flush(long timeoutMillis) {
    maybeSchedule(true, true);
    try {
      pendingCount.waitTillZero(timeoutMillis, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      options.getLogger().log(SentryLevel.ERROR, "Failed to flush metrics events", e);
      Thread.currentThread().interrupt();
    }
  }

  private void flush() {
    flushInternal();
    try (final @NotNull ISentryLifecycleToken ignored = scheduleLock.acquire()) {
      if (!queue.isEmpty()) {
        maybeSchedule(true, false);
      } else {
        hasScheduled = false;
      }
    }
  }

  private void flushInternal() {
    do {
      flushBatch();
    } while (queue.size() >= MAX_BATCH_SIZE);
  }

  private void flushBatch() {
    final @NotNull List<SentryMetricsEvent> metricsEvents = new ArrayList<>(MAX_BATCH_SIZE);
    do {
      final @Nullable SentryMetricsEvent metricsEvent = queue.poll();
      if (metricsEvent != null) {
        metricsEvents.add(metricsEvent);
      }
    } while (!queue.isEmpty() && metricsEvents.size() < MAX_BATCH_SIZE);

    if (!metricsEvents.isEmpty()) {
      client.captureBatchedMetricsEvents(new SentryMetricsEvents(metricsEvents));
      for (int i = 0; i < metricsEvents.size(); i++) {
        pendingCount.decrement();
      }
    }
  }

  private class BatchRunnable implements Runnable {

    @Override
    public void run() {
      flush();
    }
  }
}
