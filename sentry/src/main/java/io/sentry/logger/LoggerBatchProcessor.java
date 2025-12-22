package io.sentry.logger;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.DataCategory;
import io.sentry.ISentryClient;
import io.sentry.ISentryExecutorService;
import io.sentry.ISentryLifecycleToken;
import io.sentry.SentryExecutorService;
import io.sentry.SentryLevel;
import io.sentry.SentryLogEvent;
import io.sentry.SentryLogEvents;
import io.sentry.SentryOptions;
import io.sentry.clientreport.DiscardReason;
import io.sentry.transport.ReusableCountLatch;
import io.sentry.util.AutoClosableReentrantLock;
import io.sentry.util.JsonSerializationUtils;
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
public class LoggerBatchProcessor implements ILoggerBatchProcessor {

  public static final int FLUSH_AFTER_MS = 5000;
  public static final int MAX_BATCH_SIZE = 100;
  public static final int MAX_QUEUE_SIZE = 1000;

  protected final @NotNull SentryOptions options;
  private final @NotNull ISentryClient client;
  private final @NotNull Queue<SentryLogEvent> queue;
  private final @NotNull ISentryExecutorService executorService;
  private volatile @Nullable Future<?> scheduledFlush;
  private static final @NotNull AutoClosableReentrantLock scheduleLock =
      new AutoClosableReentrantLock();
  private volatile boolean hasScheduled = false;

  private final @NotNull ReusableCountLatch pendingCount = new ReusableCountLatch();

  public LoggerBatchProcessor(
      final @NotNull SentryOptions options, final @NotNull ISentryClient client) {
    this.options = options;
    this.client = client;
    this.queue = new ConcurrentLinkedQueue<>();
    this.executorService = new SentryExecutorService(options);
  }

  @Override
  public void add(final @NotNull SentryLogEvent logEvent) {
    if (pendingCount.getCount() >= MAX_QUEUE_SIZE) {
      options
          .getClientReportRecorder()
          .recordLostEvent(DiscardReason.QUEUE_OVERFLOW, DataCategory.LogItem);
      final long lostBytes =
          JsonSerializationUtils.byteSizeOf(options.getSerializer(), options.getLogger(), logEvent);
      options
          .getClientReportRecorder()
          .recordLostEvent(DiscardReason.QUEUE_OVERFLOW, DataCategory.LogByte, lostBytes);
      return;
    }
    pendingCount.increment();
    queue.offer(logEvent);
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
              .log(SentryLevel.WARNING, "Logs batch processor flush task rejected", e);
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
      options.getLogger().log(SentryLevel.ERROR, "Failed to flush log events", e);
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
    final @NotNull List<SentryLogEvent> logEvents = new ArrayList<>(MAX_BATCH_SIZE);
    do {
      final @Nullable SentryLogEvent logEvent = queue.poll();
      if (logEvent != null) {
        logEvents.add(logEvent);
      }
    } while (!queue.isEmpty() && logEvents.size() < MAX_BATCH_SIZE);

    if (!logEvents.isEmpty()) {
      client.captureBatchedLogEvents(new SentryLogEvents(logEvents));
      for (int i = 0; i < logEvents.size(); i++) {
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
