package io.sentry;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Sentry Executor Service that sends cached events and envelopes on App start.
 *
 * <p>Implementations that drop a task instead of running it — a no-op service, a full work queue —
 * must report that through the returned {@link Future}: either throw {@link
 * RejectedExecutionException} or return a {@link CancelledFuture}. Callers rely on this to tell a
 * queued task from a dropped one, so a Future that will never complete and never reports
 * cancellation leaves them waiting on a task that is never coming.
 */
@ApiStatus.Internal
public interface ISentryExecutorService {

  /**
   * Submits a Runnable to the ThreadExecutor
   *
   * @param runnable the Runnable
   * @return a Future of the Runnable, already cancelled if the task will not be run
   */
  @NotNull
  Future<?> submit(final @NotNull Runnable runnable) throws RejectedExecutionException;

  /**
   * Submits a Callable to the ThreadExecutor
   *
   * @param callable the Callable
   * @return a Future of the Callable, already cancelled if the task will not be run
   */
  @NotNull
  <T> Future<T> submit(final @NotNull Callable<T> callable) throws RejectedExecutionException;

  /**
   * Schedules a Runnable on the ThreadExecutor
   *
   * @param runnable the Runnable
   * @param delayMillis how long to wait before running it
   * @return a Future of the Runnable, already cancelled if the task will not be run
   */
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
}
