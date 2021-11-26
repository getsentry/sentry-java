package io.sentry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** Utilities to deal with dates */
@ApiStatus.Internal
public final class DateUtils {
  private static final String UTC = "UTC";
  // ISO 8601
  private static final String ISO_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";
  private static final String ISO_FORMAT_WITH_MILLIS = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
  private static final String ISO_FORMAT_MINUTES_PRECISION = "yyyy-MM-dd'T'HH:mm:00'Z'";

  // if UTC is not found, it fallback to "GMT" which is UTC equivalent
  private static final @NotNull TimeZone UTC_TIMEZONE = TimeZone.getTimeZone(UTC);

  private static final @NotNull ThreadLocal<SimpleDateFormat> SDF_ISO_FORMAT_WITH_MILLIS_UTC =
      new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
          final SimpleDateFormat simpleDateFormat =
              new SimpleDateFormat(ISO_FORMAT_WITH_MILLIS, Locale.ROOT);
          simpleDateFormat.setTimeZone(UTC_TIMEZONE);
          return simpleDateFormat;
        }
      };

  private static final @NotNull ThreadLocal<SimpleDateFormat> SDF_ISO_FORMAT_UTC =
      new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
          final SimpleDateFormat simpleDateFormat = new SimpleDateFormat(ISO_FORMAT, Locale.ROOT);
          simpleDateFormat.setTimeZone(UTC_TIMEZONE);
          return simpleDateFormat;
        }
      };

  private static final @NotNull ThreadLocal<SimpleDateFormat> SDF_ISO_FORMAT_MINUTES_PRECISION =
      new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
          final SimpleDateFormat simpleDateFormat =
              new SimpleDateFormat(ISO_FORMAT_MINUTES_PRECISION, Locale.ROOT);
          simpleDateFormat.setTimeZone(UTC_TIMEZONE);
          return simpleDateFormat;
        }
      };

  private DateUtils() {}

  /**
   * Get the current Date (UTC)
   *
   * @return the UTC Date
   */
  @SuppressWarnings("JdkObsolete")
  public static @NotNull Date getCurrentDateTime() {
    final Calendar calendar = Calendar.getInstance(UTC_TIMEZONE);
    return calendar.getTime();
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
      return SDF_ISO_FORMAT_WITH_MILLIS_UTC.get().parse(timestamp);
    } catch (ParseException e) {
      try {
        // to keep compatibility with older envelopes
        return SDF_ISO_FORMAT_UTC.get().parse(timestamp);
      } catch (ParseException ignored) {
        // invalid timestamp format
      }
      throw new IllegalArgumentException("timestamp is not ISO format " + timestamp);
    }
  }

  /**
   * Get the Date from millis timestamp
   *
   * @param timestamp millis eg 1581410911.988 (1581410911 seconds and 988 millis)
   * @return the UTC Date
   */
  @SuppressWarnings("JdkObsolete")
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
    final DateFormat df = SDF_ISO_FORMAT_WITH_MILLIS_UTC.get();
    return df.format(date);
  }

  /**
   * Get the Date from millis timestamp
   *
   * @param millis the UTC millis from the epoch
   * @return the UTC Date
   */
  public static @NotNull Date getDateTime(final long millis) {
    final Calendar calendar = Calendar.getInstance(UTC_TIMEZONE);
    calendar.setTimeInMillis(millis);
    return calendar.getTime();
  }

  /**
   * Gets the timestamp with minutes prcecision from date.
   *
   * @param date the UTC date
   * @return the string timestamp
   */
  public static @NotNull String getTimestampMinutesPrecision(final @NotNull Date date) {
    return SDF_ISO_FORMAT_MINUTES_PRECISION.get().format(date);
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
   * Convert {@link Date} to epoch time in seconds represented as {@link Double}.
   *
   * @param date - date
   * @return seconds
   */
  @SuppressWarnings("JavaUtilDate")
  public static double dateToSeconds(final @NotNull Date date) {
    return millisToSeconds(date.getTime());
  }
}
