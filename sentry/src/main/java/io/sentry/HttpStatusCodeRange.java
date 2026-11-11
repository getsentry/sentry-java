package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * The Http status code range. Example for a range: 400 to 499, 500 to 599, 400 to 599 The range is
 * inclusive so the min and max is considered part of the range.
 *
 * <p>Example for a single status code 400, 500
 */
public final class HttpStatusCodeRange {
  public static final int DEFAULT_MIN = 500;
  public static final int DEFAULT_MAX = 599;

  private static final int MIN_HTTP_STATUS_CODE = 100;
  private static final int MAX_HTTP_STATUS_CODE = 599;

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

  /**
   * Parses a single HTTP status code or an inclusive range such as {@code 404} or {@code 500-599}.
   *
   * @param value the status code or range
   * @return the parsed range
   * @throws IllegalArgumentException if the value is malformed, outside 100-599, or reversed
   */
  @ApiStatus.Internal
  public static @NotNull HttpStatusCodeRange valueOf(final @NotNull String value) {
    final String trimmedValue = value.trim();
    final int separatorIndex = trimmedValue.indexOf('-');
    if (separatorIndex < 0) {
      return new HttpStatusCodeRange(parseStatusCode(trimmedValue));
    }
    if (separatorIndex == 0
        || separatorIndex == trimmedValue.length() - 1
        || separatorIndex != trimmedValue.lastIndexOf('-')) {
      throw invalidRange(value);
    }

    final int min = parseStatusCode(trimmedValue.substring(0, separatorIndex).trim());
    final int max = parseStatusCode(trimmedValue.substring(separatorIndex + 1).trim());
    if (min > max) {
      throw invalidRange(value);
    }
    return new HttpStatusCodeRange(min, max);
  }

  private static int parseStatusCode(final @NotNull String value) {
    for (int i = 0; i < value.length(); i++) {
      if (!Character.isDigit(value.charAt(i))) {
        throw invalidRange(value);
      }
    }

    final int statusCode;
    try {
      statusCode = Integer.parseInt(value);
    } catch (NumberFormatException e) {
      throw invalidRange(value);
    }
    if (statusCode < MIN_HTTP_STATUS_CODE || statusCode > MAX_HTTP_STATUS_CODE) {
      throw invalidRange(value);
    }
    return statusCode;
  }

  private static @NotNull IllegalArgumentException invalidRange(final @NotNull String value) {
    return new IllegalArgumentException(
        "HTTP status code range must contain values from 100 to 599: " + value);
  }

  public boolean isInRange(final int statusCode) {
    return statusCode >= min && statusCode <= max;
  }
}
