package io.sentry;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** Sentry Executor Service that sends cached events and envelopes on App. start. */
@ApiStatus.Internal
public interface ISentryExecutorService {

  /**
   * Submits a Runnable to the ThreadExecutor
   *
   * @param runnable the Runnable
   * @return a Future of the Runnable
   */
  @NotNull
  Future<?> submit(final @NotNull Runnable runnable) throws RejectedExecutionException;

  /**
   * Submits a Callable to the ThreadExecutor
   *
   * @param callable the Callable
   * @return a Future of the Callable
   */
  @NotNull
  <T> Future<T> submit(final @NotNull Callable<T> callable) throws RejectedExecutionException;

  @NotNull
  Future<?> schedule(final @NotNull Runnable runnable, final long delayMillis)
      throws RejectedExecutionException;

  /**
   * Closes the ThreadExecutor and awaits for the timeout
   *
   * @param timeoutMillis the timeout in millis
   */
  void close(long timeoutMillis);

  /**
   * Check if there was a previous call to the close() method.
   *
   * @return If the executorService was previously closed
   */
  boolean isClosed();

  /** Pre-warms the executor service by increasing the initial queue capacity. SHOULD be called
   * directly after instantiating this executor service.
   */
  void prewarm();
}
