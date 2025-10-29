package io.sentry;

import io.sentry.util.AutoClosableReentrantLock;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
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
  public @NotNull Future<?> submit(final @NotNull Runnable runnable)
      throws RejectedExecutionException {
    return executorService.submit(runnable);
  }

  @Override
  public @NotNull <T> Future<T> submit(final @NotNull Callable<T> callable)
      throws RejectedExecutionException {
    return executorService.submit(callable);
  }

  @Override
  public @NotNull Future<?> schedule(final @NotNull Runnable runnable, final long delayMillis)
      throws RejectedExecutionException {
    return executorService.schedule(runnable, delayMillis, TimeUnit.MILLISECONDS);
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
    try {
      executorService.submit(
          () -> {
            try {
              // schedule a bunch of dummy runnables in the future that will never execute to
              // trigger
              // queue growth and then purge the queue
              for (int i = 0; i < INITIAL_QUEUE_SIZE; i++) {
                final Future<?> future =
                    executorService.schedule(dummyRunnable, 365L, TimeUnit.DAYS);
                future.cancel(true);
              }
              executorService.purge();
            } catch (RejectedExecutionException ignored) {
              // ignore
            }
          });
    } catch (RejectedExecutionException e) {
      if (options != null) {
        options
            .getLogger()
            .log(SentryLevel.WARNING, "Prewarm task rejected from " + executorService, e);
      }
    }
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
}
