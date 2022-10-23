package io.sentry.exception;

import org.jetbrains.annotations.Nullable;

/**
 * Used for holding a HTTP client error, for example. An integration that does not throw when API
 * returns 5xx.
 */
public final class SentryHttpClientException extends Exception {
  private static final long serialVersionUID = 1L;

  public SentryHttpClientException(final @Nullable String message) {
    super(message);
  }
}
