package io.sentry;

import io.sentry.util.AutoClosableReentrantLock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

@ApiStatus.Internal
public final class SentryExecutorService implements ISentryExecutorService {

  /**
   * ScheduledThreadPoolExecutor grows work queue by 50% each time. With the initial capacity of 16
   * it will have to resize 4 times to reach 40, which is a decent middle-ground for prewarming.
   * This will prevent from growing in unexpected areas of the SDK.
   */
  private static final int INITIAL_QUEUE_SIZE = 40;

  /**
   * By default, the work queue is unbounded so it can grow as much as the memory allows. We want to
   * limit it by 271 which would be x8 times growth from the default initial capacity.
   */
  private static final int MAX_QUEUE_SIZE = 271;

  private final @NotNull ScheduledThreadPoolExecutor executorService;
  private final @NotNull AutoClosableReentrantLock lock = new AutoClosableReentrantLock();

  @SuppressWarnings("UnnecessaryLambda")
  private final @NotNull Runnable dummyRunnable = () -> {};

  private final @Nullable SentryOptions options;

  @TestOnly
  SentryExecutorService(
      final @NotNull ScheduledThreadPoolExecutor executorService,
      final @Nullable SentryOptions options) {
    this.executorService = executorService;
    this.options = options;
  }

  public SentryExecutorService(final @Nullable SentryOptions options) {
    this(new ScheduledThreadPoolExecutor(1, new SentryExecutorServiceThreadFactory()), options);
  }

  public SentryExecutorService() {
    this(new ScheduledThreadPoolExecutor(1, new SentryExecutorServiceThreadFactory()), null);
  }

  @Override
  public @NotNull Future<?> submit(final @NotNull Runnable runnable) {
    if (executorService.getQueue().size() < MAX_QUEUE_SIZE) {
      return executorService.submit(runnable);
    }
    // TODO: maybe RejectedExecutionException?
    if (options != null) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Task " + runnable + " rejected from " + executorService);
    }
    return new CancelledFuture<>();
  }

  @Override
  public @NotNull <T> Future<T> submit(final @NotNull Callable<T> callable) {
    if (executorService.getQueue().size() < MAX_QUEUE_SIZE) {
      return executorService.submit(callable);
    }
    // TODO: maybe RejectedExecutionException?
    if (options != null) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Task " + callable + " rejected from " + executorService);
    }
    return new CancelledFuture<>();
  }

  @Override
  public @NotNull Future<?> schedule(final @NotNull Runnable runnable, final long delayMillis) {
    if (executorService.getQueue().size() < MAX_QUEUE_SIZE) {
      return executorService.schedule(runnable, delayMillis, TimeUnit.MILLISECONDS);
    }
    // TODO: maybe RejectedExecutionException?
    if (options != null) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Task " + runnable + " rejected from " + executorService);
    }
    return new CancelledFuture<>();
  }

  @Override
  public void close(final long timeoutMillis) {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      if (!executorService.isShutdown()) {
        executorService.shutdown();
        try {
          if (!executorService.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS)) {
            executorService.shutdownNow();
          }
        } catch (InterruptedException e) {
          executorService.shutdownNow();
          Thread.currentThread().interrupt();
        }
      }
    }
  }

  @Override
  public boolean isClosed() {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      return executorService.isShutdown();
    }
  }

  @SuppressWarnings({"FutureReturnValueIgnored"})
  @Override
  public void prewarm() {
    executorService.submit(
        () -> {
          // Schedule dummy tasks with a reasonable delay to trigger queue growth
          // Use a delay that won't cause integer overflow when converted to nanoseconds
          // 365 days is safe (Long.MAX_VALUE / TimeUnit.DAYS.toNanos(1) > 365)
          final long safeDelayDays = 365L;

          // Store references to the scheduled futures so we can cancel them properly
          // since scheduled tasks are stored in DelayQueue, not the main work queue
          final List<ScheduledFuture<?>> scheduledTasks = new ArrayList<>(INITIAL_QUEUE_SIZE);

          try {
            // Schedule dummy tasks to trigger queue growth
            for (int i = 0; i < INITIAL_QUEUE_SIZE; i++) {
              ScheduledFuture<?> future =
                  executorService.schedule(dummyRunnable, safeDelayDays, TimeUnit.DAYS);
              scheduledTasks.add(future);
            }
          } finally {
            // Cancel all scheduled dummy tasks to prevent memory leak
            // This must be done in the same task to avoid race conditions
            for (ScheduledFuture<?> future : scheduledTasks) {
              future.cancel(false);
            }

            // Clear any remaining tasks from the main work queue
            // This is safe now since we're in a single task execution
            executorService.getQueue().clear();
          }
        });
  }

  private static final class SentryExecutorServiceThreadFactory implements ThreadFactory {
    private int cnt;

    @Override
    public @NotNull Thread newThread(final @NotNull Runnable r) {
      final Thread ret = new Thread(r, "SentryExecutorServiceThreadFactory-" + cnt++);
      ret.setDaemon(true);
      return ret;
    }
  }

  private static final class CancelledFuture<T> implements Future<T> {
    @Override
    public boolean cancel(final boolean mayInterruptIfRunning) {
      return true;
    }

    @Override
    public boolean isCancelled() {
      return true;
    }

    @Override
    public boolean isDone() {
      return true;
    }

    @Override
    public T get() {
      throw new CancellationException();
    }

    @Override
    public T get(final long timeout, final @NotNull TimeUnit unit) {
      throw new CancellationException();
    }
  }
}
