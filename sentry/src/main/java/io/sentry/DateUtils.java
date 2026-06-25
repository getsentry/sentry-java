package io.sentry;

import io.sentry.vendor.SentryIso8601Utils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Utilities to deal with dates */
@ApiStatus.Internal
@SuppressWarnings("JavaUtilDate")
public final class DateUtils {

  private DateUtils() {}

  /**
   * Get the current Date (UTC)
   *
   * @return the UTC Date
   */
  @SuppressWarnings("JavaUtilDate")
  public static @NotNull Date getCurrentDateTime() {
    return new Date();
  }

  /**
   * Get the Date from UTC/ISO 8601 timestamp
   *
   * @param timestamp UTC/ISO 8601 format eg 2000-12-31T23:59:58Z or 2000-12-31T23:59:58.123Z
   * @return the UTC Date
   */
  public static @NotNull Date getDateTime(final @NotNull String timestamp)
      throws IllegalArgumentException {
    try {
      return getDateTime(SentryIso8601Utils.parseTimestamp(timestamp));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("timestamp is not ISO format " + timestamp);
    }
  }

  /**
   * Get the Date from millis timestamp
   *
   * @param timestamp millis eg 1581410911.988 (1581410911 seconds and 988 millis)
   * @return the UTC Date
   */
  public static @NotNull Date getDateTimeWithMillisPrecision(final @NotNull String timestamp)
      throws IllegalArgumentException {
    try {
      return getDateTime(
          new BigDecimal(timestamp).setScale(3, RoundingMode.DOWN).movePointRight(3).longValue());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("timestamp is not millis format " + timestamp);
    }
  }

  /**
   * Get the UTC/ISO 8601 timestamp from Date
   *
   * @param date the UTC Date
   * @return the UTC/ISO 8601 timestamp
   */
  public static @NotNull String getTimestamp(final @NotNull Date date) {
    return getTimestampFromMillis(date.getTime());
  }

  /**
   * Get the UTC/ISO 8601 timestamp from millis.
   *
   * @param millis the UTC millis from the epoch
   * @return the UTC/ISO 8601 timestamp
   */
  static @NotNull String getTimestampFromMillis(final long millis) {
    return SentryIso8601Utils.formatTimestamp(millis);
  }

  /**
   * Get the Date from millis timestamp
   *
   * @param millis the UTC millis from the epoch
   * @return the UTC Date
   */
  @SuppressWarnings("JavaUtilDate")
  public static @NotNull Date getDateTime(final long millis) {
    return new Date(millis);
  }

  /**
   * Converts milliseconds to seconds.
   *
   * @param millis - milliseconds
   * @return seconds
   */
  public static double millisToSeconds(final double millis) {
    return millis / 1000;
  }

  public static long millisToNanos(final long millis) {
    return millis * 1000000L;
  }

  /**
   * Converts nanoseconds to milliseconds
   *
   * @param nanos - nanoseconds
   * @return milliseconds
   */
  public static double nanosToMillis(final double nanos) {
    return nanos / 1000000;
  }

  /**
   * Converts nanoseconds to {@link java.util.Date} rounded down to milliseconds
   *
   * @param nanos - nanoseconds
   * @return date rounded down to milliseconds
   */
  public static Date nanosToDate(final long nanos) {
    final double millis = nanosToMillis((double) nanos);
    return getDateTime((long) millis);
  }

  public static @Nullable Date toUtilDate(final @Nullable SentryDate sentryDate) {
    if (sentryDate == null) {
      return null;
    }
    return toUtilDateNotNull(sentryDate);
  }

  public static @NotNull Date toUtilDateNotNull(final @NotNull SentryDate sentryDate) {
    return nanosToDate(sentryDate.nanoTimestamp());
  }

  /**
   * Converts nanoseconds to seconds
   *
   * @param nanos - nanoseconds
   * @return seconds
   */
  public static double nanosToSeconds(final long nanos) {
    return (double) nanos / (1000.0 * 1000.0 * 1000.0);
  }

  /**
   * Convert {@link Date} to epoch time in seconds represented as {@link Double}.
   *
   * @param date - date
   * @return seconds
   */
  @SuppressWarnings("JavaUtilDate")
  public static double dateToSeconds(final @NotNull Date date) {
    return millisToSeconds(date.getTime());
  }

  public static long secondsToNanos(final @NotNull long seconds) {
    return seconds * (1000L * 1000L * 1000L);
  }

  public static @NotNull BigDecimal doubleToBigDecimal(final double value) {
    return BigDecimal.valueOf(value).setScale(6, RoundingMode.DOWN);
  }
}
