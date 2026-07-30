package io.sentry;

import io.sentry.transport.ReusableCountLatch;
import io.sentry.util.AutoClosableReentrantLock;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
final class AsyncEventProcessingExecutor {
  private final int maxQueueSize;
  private final @NotNull ILogger logger;
  private final @NotNull ThreadPoolExecutor executor;
  private final @NotNull ReusableCountLatch unfinishedTasksCount = new ReusableCountLatch();
  private final @NotNull AtomicBoolean closed = new AtomicBoolean(false);

  /**
   * Guards the admission check and the matching increment so concurrent submits cannot both observe
   * the same free slot. Never held across a blocking call: a capture on an app thread must not
   * stall behind a shutdown that is draining the queue.
   */
  private final @NotNull AutoClosableReentrantLock submitLock = new AutoClosableReentrantLock();

  AsyncEventProcessingExecutor(final int maxQueueSize, final @NotNull ILogger logger) {
    this.maxQueueSize = maxQueueSize;
    this.logger = logger;
    this.executor =
        new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(),
            new AsyncEventProcessingThreadFactory());
  }

  /**
   * Accepts a task for async processing.
   *
   * @param task the processing to run off the caller thread
   * @param onDropped invoked if the task is discarded during shutdown, so the caller can record the
   *     matching client report. Never invoked when this method returns {@code false}; the caller
   *     reports the rejection itself in that case.
   * @return true if the task was accepted
   */
  boolean submit(final @NotNull Runnable task, final @NotNull Runnable onDropped) {
    try (final @NotNull ISentryLifecycleToken ignored = submitLock.acquire()) {
      if (closed.get() || unfinishedTasksCount.getCount() >= maxQueueSize) {
        return false;
      }
      unfinishedTasksCount.increment();
    }

    try {
      executor.execute(new AsyncTask(task, onDropped));
      return true;
    } catch (Throwable e) {
      unfinishedTasksCount.decrement();
      logger.log(SentryLevel.WARNING, "Async event processing task rejected.", e);
      return false;
    }
  }

  boolean isClosed() {
    return closed.get();
  }

  void waitTillIdle(final long timeoutMillis) {
    try {
      if (!unfinishedTasksCount.waitTillZero(timeoutMillis, TimeUnit.MILLISECONDS)) {
        logger.log(
            SentryLevel.WARNING,
            "Timed out waiting for async event processing queue to drain, %d task(s) still pending.",
            unfinishedTasksCount.getCount());
      }
    } catch (InterruptedException e) {
      logger.log(
          SentryLevel.DEBUG,
          "Interrupted while waiting for async event processing queue to drain.");
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Stops accepting new work and drains what was already accepted. Must run before downstream
   * queues are flushed, otherwise tasks finishing here would hand envelopes to an already-drained
   * transport.
   */
  void close(final long timeoutMillis) {
    closed.set(true);
    executor.shutdown();
    try {
      if (!executor.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS)) {
        reportDropped(executor.shutdownNow());
      }
    } catch (InterruptedException e) {
      reportDropped(executor.shutdownNow());
      Thread.currentThread().interrupt();
    }
  }

  /** Records a client report for every task that never got to run, so drops stay accounted for. */
  private void reportDropped(final @NotNull List<Runnable> neverRun) {
    if (neverRun.isEmpty()) {
      return;
    }
    logger.log(
        SentryLevel.WARNING,
        "Dropping %d async event processing task(s) that did not finish before shutdown.",
        neverRun.size());
    for (final Runnable runnable : neverRun) {
      if (runnable instanceof AsyncTask) {
        ((AsyncTask) runnable).drop();
      }
    }
  }

  private final class AsyncTask implements Runnable {
    private final @NotNull Runnable task;
    private final @NotNull Runnable onDropped;

    AsyncTask(final @NotNull Runnable task, final @NotNull Runnable onDropped) {
      this.task = task;
      this.onDropped = onDropped;
    }

    @Override
    public void run() {
      try {
        task.run();
      } finally {
        unfinishedTasksCount.decrement();
      }
    }

    void drop() {
      try {
        onDropped.run();
      } catch (Throwable e) {
        logger.log(SentryLevel.ERROR, "Failed to record dropped async event processing task.", e);
      } finally {
        unfinishedTasksCount.decrement();
      }
    }
  }

  private static final class AsyncEventProcessingThreadFactory implements ThreadFactory {
    private int cnt;

    @Override
    public @NotNull Thread newThread(final @NotNull Runnable r) {
      final Thread thread = new Thread(r, "SentryAsyncEventProcessing-" + cnt++);
      thread.setDaemon(true);
      return thread;
    }
  }
}
