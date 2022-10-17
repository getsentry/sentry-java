package io.sentry;

/**
 * The Http status code range. Example for a range: 400 to 499, 500 to 599, 400 to 599 The range is
 * inclusive so the min and max is considered part of the range.
 *
 * <p>Example for a single status code 400, 500
 */
public final class HttpStatusCodeRange {
  public static final int DEFAULT_MIN = 500;
  public static final int DEFAULT_MAX = 599;

  private final int min;
  private final int max;

  public HttpStatusCodeRange(final int min, final int max) {
    this.min = min;
    this.max = max;
  }

  public HttpStatusCodeRange(final int statusCode) {
    this.min = statusCode;
    this.max = statusCode;
  }

  public boolean isInRange(final int statusCode) {
    return statusCode >= min && statusCode <= max;
  }
}
