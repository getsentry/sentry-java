package io.sentry;

import java.util.concurrent.Future;
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
  Future<?> submit(final @NotNull Runnable runnable);

  @NotNull
  Future<?> schedule(final @NotNull Runnable runnable, final long delayMillis);

  /**
   * Closes the ThreadExecutor and awaits for the timeout
   *
   * @param timeoutMillis the timeout in millis
   */
  void close(long timeoutMillis);
}
