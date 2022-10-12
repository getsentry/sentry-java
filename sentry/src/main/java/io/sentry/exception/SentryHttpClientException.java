package io.sentry.exception;

import org.jetbrains.annotations.Nullable;

/**
 * Used for holding a HTTP client error, for example. An integration that does not throw when API
 * returns 5xx.
 */
public class SentryHttpClientException extends Exception {

  public SentryHttpClientException(final @Nullable String message) {
    super(message);
  }
}
