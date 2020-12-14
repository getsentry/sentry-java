package io.sentry;

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

  private static @NotNull TimeZone getTimeZone() {
    TimeZone timeZone = TimeZone.getTimeZone(UTC);
    // if UTC is not available, let's get the current one and avoid NPE problems
    if (timeZone == null) {
      timeZone = TimeZone.getDefault();
    }
    return timeZone;
  }

  private static final @NotNull TimeZone UTC_TIMEZONE = getTimeZone();

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
          final SimpleDateFormat simpleDateFormat =
                  new SimpleDateFormat(ISO_FORMAT, Locale.ROOT);
          simpleDateFormat.setTimeZone(UTC_TIMEZONE);
          return simpleDateFormat;
        }
      };

  private DateUtils() {}

  /**
   * Get the current date and time (UTC)
   *
   * @return the UTC date and time
   */
  @SuppressWarnings("JdkObsolete")
  public static @NotNull Date getCurrentDateTime() {
    final Calendar calendar = Calendar.getInstance(UTC_TIMEZONE);
    return calendar.getTime();
  }

  /**
   * Get Java Date from UTC/ISO 8601 timestamp format
   *
   * @param timestamp UTC/ISO 8601 format eg 2000-12-31T23:59:58Z or 2000-12-31T23:59:58.123Z
   * @return the Date
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
   * Get Java Date from millis timestamp format
   *
   * @param timestamp millis format eg 1581410911.988 (1581410911 seconds and 988 millis)
   * @return the Date UTC timezone
   */
  @SuppressWarnings("JdkObsolete")
  public static @NotNull Date getDateTimeWithMillisPrecision(final @NotNull String timestamp)
      throws IllegalArgumentException {
    try {
      final String[] times = timestamp.split("\\.", -1);
      final long seconds = Long.parseLong(times[0]);
      final long millis = times.length > 1 ? Long.parseLong(times[1]) : 0;

      return getDateTime((seconds * 1000) + millis);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("timestamp is not millis format " + timestamp);
    }
  }

  /**
   * Get date formatted as expected by Sentry.
   *
   * @param date already UTC format
   * @return the ISO formatted date with millis precision.
   */
  public static @NotNull String getTimestamp(final @NotNull Date date) {
    final DateFormat df = SDF_ISO_FORMAT_WITH_MILLIS_UTC.get();
    return df.format(date);
  }

  public static @NotNull Date getDateTime(final long millis) {
    final Calendar calendar = Calendar.getInstance(UTC_TIMEZONE);
    calendar.setTimeInMillis(millis);
    return calendar.getTime();
  }
}
