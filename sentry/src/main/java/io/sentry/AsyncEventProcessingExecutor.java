package io.sentry;

import io.sentry.transport.ReusableCountLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
final class AsyncEventProcessingExecutor {
  private final int maxQueueSize;
  private final @NotNull ILogger logger;
  private final @NotNull ThreadPoolExecutor executor;
  private final @NotNull ReusableCountLatch unfinishedTasksCount = new ReusableCountLatch();
  private boolean closed = false;

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

  synchronized boolean submit(final @NotNull Runnable task) {
    if (closed || unfinishedTasksCount.getCount() >= maxQueueSize) {
      return false;
    }

    unfinishedTasksCount.increment();
    try {
      executor.execute(
          () -> {
            try {
              task.run();
            } finally {
              unfinishedTasksCount.decrement();
            }
          });
      return true;
    } catch (Throwable e) {
      unfinishedTasksCount.decrement();
      logger.log(SentryLevel.WARNING, "Async event processing task rejected.", e);
      return false;
    }
  }

  void waitTillIdle(final long timeoutMillis) {
    try {
      unfinishedTasksCount.waitTillZero(timeoutMillis, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      logger.log(SentryLevel.ERROR, "Failed to wait for async event processing queue to drain.", e);
      Thread.currentThread().interrupt();
    }
  }

  synchronized void close(final long timeoutMillis) {
    closed = true;
    executor.shutdown();
    try {
      if (!executor.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS)) {
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      executor.shutdownNow();
      Thread.currentThread().interrupt();
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
