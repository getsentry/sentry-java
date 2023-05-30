package io.sentry;

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

final class SentryExecutorService implements ISentryExecutorService {

  private final @NotNull ScheduledExecutorService executorService;

  @TestOnly
  SentryExecutorService(final @NotNull ScheduledExecutorService executorService) {
    this.executorService = executorService;
  }

  SentryExecutorService() {
    this(Executors.newSingleThreadScheduledExecutor(new SentryExecutorServiceThreadFactory()));
  }

  @Override
  public @NotNull Future<?> submit(final @NotNull Runnable runnable) {
    return executorService.submit(runnable);
  }

  @Override
  public @NotNull <T> Future<T> submit(final @NotNull Callable<T> callable) {
    return executorService.submit(callable);
  }

  @Override
  public @NotNull Future<?> schedule(final @NotNull Runnable runnable, final long delayMillis) {
    return executorService.schedule(runnable, delayMillis, TimeUnit.MILLISECONDS);
  }

  @Override
  public void close(final long timeoutMillis) {
    synchronized (executorService) {
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
    synchronized (executorService) {
      return executorService.isShutdown();
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
