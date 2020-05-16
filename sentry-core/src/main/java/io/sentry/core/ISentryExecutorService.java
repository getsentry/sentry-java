package io.sentry.core;

import java.util.concurrent.Future;

/** Sentry Executor Service that sends cached events and envelopes on App. start. */
interface ISentryExecutorService {

  /**
   * Submits a Runnable to the ThreadExecutor
   *
   * @param runnable the Runnable
   * @return a Future of the Runnable
   */
  Future<?> submit(Runnable runnable);

  /**
   * Closes the ThreadExecutor and awaits for the timeout
   *
   * @param timeoutMillis the timeout in millis
   */
  void close(long timeoutMillis);
}
