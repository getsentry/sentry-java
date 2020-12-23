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

  // if UTC is not found, it fallback to "GMT" which is UTC equivalent
  private static final @NotNull TimeZone UTC_TIMEZONE = TimeZone.getTimeZone(UTC);

  static @NotNull TimeZone getUtCTimezone() {
    return UTC_TIMEZONE;
  }

  private static final @NotNull ThreadLocal<SimpleDateFormat> SDF_ISO_FORMAT_WITH_MILLIS_UTC =
      new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
          final SimpleDateFormat simpleDateFormat =
              new SimpleDateFormat(ISO_FORMAT_WITH_MILLIS, Locale.ROOT);
          simpleDateFormat.setTimeZone(getUtCTimezone());
          return simpleDateFormat;
        }
      };

  private static final @NotNull ThreadLocal<SimpleDateFormat> SDF_ISO_FORMAT_UTC =
      new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
          final SimpleDateFormat simpleDateFormat = new SimpleDateFormat(ISO_FORMAT, Locale.ROOT);
          simpleDateFormat.setTimeZone(getUtCTimezone());
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
      final String[] times = timestamp.split("\\.", -1);
      final long seconds = Long.parseLong(times[0]);
      final long millis = times.length > 1 ? Long.parseLong(times[1]) : 0;

      return getDateTime((seconds * 1000) + millis);
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
}
