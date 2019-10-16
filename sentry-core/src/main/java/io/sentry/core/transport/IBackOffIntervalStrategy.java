package io.sentry.core.transport;

/**
 * Implementations of this interface can influence the delay between attempts to send an event to
 * Sentry server.
 *
 * <p>Note that the suggested delay may be ignored if the Sentry server explicitly asks for a
 * different delay.
 */
public interface IBackOffIntervalStrategy {

  /**
   * Suggests the delay before the next attempt to send an event after a failure.
   *
   * @param attempt the number of attempts made so far
   * @return the suggested delay in milliseconds
   */
  long nextDelayMillis(int attempt);
}
