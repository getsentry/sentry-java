package io.sentry.logger;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.DataCategory;
import io.sentry.ISentryClient;
import io.sentry.ISentryExecutorService;
import io.sentry.SentryExecutorService;
import io.sentry.SentryLevel;
import io.sentry.SentryLogEvent;
import io.sentry.SentryLogEvents;
import io.sentry.SentryOptions;
import io.sentry.clientreport.DiscardReason;
import io.sentry.transport.ReusableCountLatch;
import io.sentry.util.JsonSerializationUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

@Open
public class LoggerBatchProcessor implements ILoggerBatchProcessor {

  public static final int FLUSH_AFTER_MS = 5000;
  public static final int MAX_BATCH_SIZE = 100;
  public static final int MAX_QUEUE_SIZE = 1000;

  protected final @NotNull SentryOptions options;
  private final @NotNull ISentryClient client;
  private final @NotNull Queue<SentryLogEvent> queue;
  private final @NotNull ISentryExecutorService executorService;
  private final @NotNull AtomicBoolean hasScheduled = new AtomicBoolean(false);
  private volatile boolean isShuttingDown = false;

  private final @NotNull ReusableCountLatch pendingCount = new ReusableCountLatch();

  public LoggerBatchProcessor(
      final @NotNull SentryOptions options, final @NotNull ISentryClient client) {
    this(options, client, new SentryExecutorService(options));
  }

  @ApiStatus.Internal
  @TestOnly
  public LoggerBatchProcessor(
      final @NotNull SentryOptions options,
      final @NotNull ISentryClient client,
      final @NotNull ISentryExecutorService executorService) {
    this.options = options;
    this.client = client;
    this.queue = new ConcurrentLinkedQueue<>();
    this.executorService = executorService;
  }

  @Override
  public void add(final @NotNull SentryLogEvent logEvent) {
    if (isShuttingDown) {
      return;
    }
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
    maybeSchedule(false);
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  @Override
  public void close(final boolean isRestarting) {
    isShuttingDown = true;
    if (isRestarting) {
      maybeSchedule(true);
      executorService.submit(() -> executorService.close(options.getShutdownTimeoutMillis()));
    } else {
      executorService.close(options.getShutdownTimeoutMillis());
      while (!queue.isEmpty()) {
        flushBatch();
      }
    }
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  private void maybeSchedule(boolean immediately) {
    if (immediately) {
      // any already scheduled task may be far in the future, we want to schedule something that
      // runs right away
      hasScheduled.set(true);
    } else if (!hasScheduled.compareAndSet(false, true)) {
      // was already true, no need to schedule again
      return;
    }
    final int flushAfterMs = immediately ? 0 : FLUSH_AFTER_MS;
    try {
      executorService.schedule(new BatchRunnable(), flushAfterMs);
    } catch (RejectedExecutionException e) {
      hasScheduled.set(false);
      options.getLogger().log(SentryLevel.WARNING, "Logs batch processor flush task rejected", e);
    }
  }

  @Override
  public void flush(long timeoutMillis) {
    maybeSchedule(true);
    try {
      pendingCount.waitTillZero(timeoutMillis, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      options.getLogger().log(SentryLevel.ERROR, "Failed to flush log events", e);
      Thread.currentThread().interrupt();
    }
  }

  private void flush() {
    flushInternal();
    hasScheduled.set(false);
    if (!queue.isEmpty()) {
      maybeSchedule(false);
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
