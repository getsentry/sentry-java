package io.sentry.core;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

final class SentryExecutorService implements ISentryExecutorService {

  private final @NotNull ExecutorService executorService;

  @TestOnly
  SentryExecutorService(final @NotNull ExecutorService executorService) {
    this.executorService = executorService;
  }

  SentryExecutorService() {
    this(Executors.newSingleThreadExecutor());
  }

  @Override
  public Future<?> submit(final @NotNull Runnable runnable) {
    return executorService.submit(runnable);
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
}
