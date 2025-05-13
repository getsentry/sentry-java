package io.sentry.logger;

import io.sentry.ISentryClient;
import io.sentry.ISentryLifecycleToken;
import io.sentry.SentryLogEvent;
import io.sentry.SentryLogEvents;
import io.sentry.SentryOptions;
import io.sentry.util.AutoClosableReentrantLock;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LoggerBatchProcessor implements ILoggerBatchProcessor {

  public static final int FLUSH_AFTER_MS = 5000;
  public static final int MAX_BATCH_SIZE = 100;

  private final @NotNull SentryOptions options;
  private final @NotNull ISentryClient client;
  private final @NotNull Queue<SentryLogEvent> queue;
  private volatile @Nullable Future<?> scheduledFlush;
  private static final @NotNull AutoClosableReentrantLock scheduleLock =
      new AutoClosableReentrantLock();

  public LoggerBatchProcessor(
      final @NotNull SentryOptions options, final @NotNull ISentryClient client) {
    this.options = options;
    this.client = client;
    this.queue = new ConcurrentLinkedQueue<>();
  }

  @Override
  public void add(final @NotNull SentryLogEvent logEvent) {
    queue.offer(logEvent);
    maybeSchedule(false, false);
  }

  @Override
  public void close(final boolean isRestarting) {
    if (isRestarting) {
      maybeSchedule(true, true);
    } else {
      while (!queue.isEmpty()) {
        flushBatch();
      }
    }
  }

  private void maybeSchedule(boolean forceSchedule, boolean immediately) {
    try (final @NotNull ISentryLifecycleToken ignored = scheduleLock.acquire()) {
      final @Nullable Future<?> latestScheduledFlush = scheduledFlush;
      if (forceSchedule
          || latestScheduledFlush == null
          || latestScheduledFlush.isDone()
          || latestScheduledFlush.isCancelled()) {
        final int flushAfterMs = immediately ? 0 : FLUSH_AFTER_MS;
        scheduledFlush = options.getExecutorService().schedule(new BatchRunnable(), flushAfterMs);
      }
    }
  }

  private void flush() {
    flushInternal();
    try (final @NotNull ISentryLifecycleToken ignored = scheduleLock.acquire()) {
      if (!queue.isEmpty()) {
        maybeSchedule(true, false);
      }
    }
  }

  private void flushInternal() {
    flushBatch();
    if (queue.size() >= MAX_BATCH_SIZE) {
      flushInternal();
    }
  }

  private void flushBatch() {
    final @NotNull List<SentryLogEvent> logEvents = new ArrayList<>(MAX_BATCH_SIZE);
    do {
      final @Nullable SentryLogEvent logEvent = queue.poll();
      if (logEvent != null) {
        logEvents.add(logEvent);
      }
    } while (!queue.isEmpty() && logEvents.size() < MAX_BATCH_SIZE);

    client.captureBatchedLogEvents(new SentryLogEvents(logEvents));
  }

  private class BatchRunnable implements Runnable {

    @Override
    public void run() {
      flush();
    }
  }
}
