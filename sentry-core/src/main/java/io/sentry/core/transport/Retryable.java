package io.sentry.core.transport;

/**
 * Internal interface using which the {@link AsyncConnection} realizes the retry after a delay
 * obtained from a response from the Sentry server.
 */
interface Retryable extends Runnable {

  /**
   * This method is called if there is an exception during the {@link #run()} method to obtain the
   * delay before the next retry.
   */
  long getSuggestedRetryDelayMillis();

  int getResponseCode();
}
