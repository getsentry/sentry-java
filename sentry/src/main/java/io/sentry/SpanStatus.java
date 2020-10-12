package io.sentry;

// TODO: add the rest of the statuses
public enum SpanStatus {
  /**
   * Not an error, returned on success. HTTP status code equivalent: 200 and 2XX
   */
  OK,
  /**
   * The operation was cancelled, typically by the caller. HTTP status code equivalent: 499
   */
  CANCELLED,
  /**
   * An unknown error raised by APIs that don't return enough error information. HTTP status code equivalent: 500
   */
  UNKNOWN,
  /**
   * An unknown error raised by APIs that don't return enough error information. HTTP status code equivalent: 500
   */
  UNKNOWN_ERROR,
  /**
   * The client specified an invalid argument. HTTP status code equivalent: 504
   */
  INVALID_ARGUMENT,
  /**
   * The deadline expired before the operation could succeed. HTTP status code equivalent: 504
   */
  DEADLINE_EXCEEDED
}
