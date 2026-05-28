package io.sentry;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class NoOpSentryExecutorService implements ISentryExecutorService {
  private static final NoOpSentryExecutorService instance = new NoOpSentryExecutorService();

  private NoOpSentryExecutorService() {}

  public static @NotNull ISentryExecutorService getInstance() {
    return instance;
  }

  @Override
  public @NotNull Future<?> submit(final @NotNull Runnable runnable) {
    return new FutureTask<>(() -> null);
  }

  @Override
  public @NotNull <T> Future<T> submit(final @NotNull Callable<T> callable) {
    return new FutureTask<>(() -> null);
  }

  @Override
  public @NotNull Future<?> schedule(@NotNull Runnable runnable, long delayMillis) {
    return new FutureTask<>(() -> null);
  }

  @Override
  public void close(long timeoutMillis) {}

  @Override
  public boolean isClosed() {
    return false;
  }

  @Override
  public void prewarm() {}
}
