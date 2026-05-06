package io.sentry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

/** Utilities to deal with dates */
@ApiStatus.Internal
public final class DateUtils {

  private static final TimeZone TIMEZONE_UTC = TimeZone.getTimeZone("UTC");

  @VisibleForTesting static final boolean HAS_JAVA_TIME;

  static {
    boolean available;
    try {
      Class.forName("java.time.Instant");
      available = true;
    } catch (ClassNotFoundException e) {
      available = false;
    }
    HAS_JAVA_TIME = available;
  }

  private DateUtils() {}

  /**
   * Get the current Date (UTC)
   *
   * @return the UTC Date
   */
  @SuppressWarnings("JdkObsolete")
  public static @NotNull Date getCurrentDateTime() {
    final Calendar calendar = Calendar.getInstance(TIMEZONE_UTC);
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
      if (HAS_JAVA_TIME) {
        return Iso8601JavaTime.parse(timestamp);
      }
      return Iso8601Legacy.parse(timestamp);
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalArgumentException("timestamp is not ISO format " + timestamp, e);
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
  @SuppressWarnings("JavaUtilDate")
  public static @NotNull String getTimestamp(final @NotNull Date date) {
    if (HAS_JAVA_TIME) {
      return Iso8601JavaTime.format(date);
    }
    return Iso8601Legacy.format(date);
  }

  /**
   * Get the Date from millis timestamp
   *
   * @param millis the UTC millis from the epoch
   * @return the UTC Date
   */
  public static @NotNull Date getDateTime(final long millis) {
    final Calendar calendar = Calendar.getInstance(TIMEZONE_UTC);
    calendar.setTimeInMillis(millis);
    return calendar.getTime();
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
    final Double millis = nanosToMillis(Double.valueOf(nanos));
    return getDateTime(millis.longValue());
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
    return Double.valueOf(nanos) / (1000.0 * 1000.0 * 1000.0);
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

  /**
   * Convert {@link Date} to nanoseconds represented as {@link Long}.
   *
   * @param date - date
   * @return nanoseconds
   */
  @SuppressWarnings("JavaUtilDate")
  public static long dateToNanos(final @NotNull Date date) {
    return millisToNanos(date.getTime());
  }

  public static long secondsToNanos(final @NotNull long seconds) {
    return seconds * (1000L * 1000L * 1000L);
  }

  public static @NotNull BigDecimal doubleToBigDecimal(final @NotNull Double value) {
    return BigDecimal.valueOf(value).setScale(6, RoundingMode.DOWN);
  }

  // region java.time-based ISO 8601 (JVM and Android API 26+)

  @SuppressWarnings("NewApi")
  static final class Iso8601JavaTime {
    private static final java.time.format.DateTimeFormatter FORMATTER =
        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(java.time.ZoneOffset.UTC);

    @SuppressWarnings("JavaUtilDate")
    static @NotNull String format(final @NotNull Date date) {
      return FORMATTER.format(java.time.Instant.ofEpochMilli(date.getTime()));
    }

    @SuppressWarnings("JavaUtilDate")
    static @NotNull Date parse(final @NotNull String timestamp) {
      try {
        java.time.OffsetDateTime odt = java.time.OffsetDateTime.parse(timestamp);
        return new Date(odt.toInstant().toEpochMilli());
      } catch (java.time.format.DateTimeParseException e) {
        try {
          java.time.LocalDate localDate = java.time.LocalDate.parse(timestamp);
          return new Date(
              localDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
        } catch (java.time.format.DateTimeParseException e2) {
          throw new IllegalArgumentException("timestamp is not ISO format " + timestamp, e);
        }
      }
    }
  }

  // endregion

  // region Legacy ISO 8601 fallback (Android API < 26 without desugaring)

  @SuppressWarnings({"MagicConstant", "JdkObsolete"})
  static final class Iso8601Legacy {
    static @NotNull String format(final @NotNull Date date) {
      Calendar calendar = new GregorianCalendar(TIMEZONE_UTC, Locale.US);
      calendar.setTime(date);
      StringBuilder sb = new StringBuilder(24);
      padInt(sb, calendar.get(Calendar.YEAR), 4);
      sb.append('-');
      padInt(sb, calendar.get(Calendar.MONTH) + 1, 2);
      sb.append('-');
      padInt(sb, calendar.get(Calendar.DAY_OF_MONTH), 2);
      sb.append('T');
      padInt(sb, calendar.get(Calendar.HOUR_OF_DAY), 2);
      sb.append(':');
      padInt(sb, calendar.get(Calendar.MINUTE), 2);
      sb.append(':');
      padInt(sb, calendar.get(Calendar.SECOND), 2);
      sb.append('.');
      padInt(sb, calendar.get(Calendar.MILLISECOND), 3);
      sb.append('Z');
      return sb.toString();
    }

    static @NotNull Date parse(final @NotNull String date) {
      int offset = 0;
      int year = parseInt(date, offset, offset += 4);
      if (checkOffset(date, offset, '-')) offset++;
      int month = parseInt(date, offset, offset += 2);
      if (checkOffset(date, offset, '-')) offset++;
      int day = parseInt(date, offset, offset += 2);

      int hour = 0;
      int minutes = 0;
      int seconds = 0;
      int milliseconds = 0;

      if (date.length() <= offset) {
        return new GregorianCalendar(year, month - 1, day).getTime();
      }

      if (checkOffset(date, offset, 'T')) {
        offset++;
        hour = parseInt(date, offset, offset += 2);
        if (checkOffset(date, offset, ':')) offset++;
        minutes = parseInt(date, offset, offset += 2);
        if (checkOffset(date, offset, ':')) offset++;

        if (offset < date.length()) {
          char c = date.charAt(offset);
          if (c != 'Z' && c != '+' && c != '-') {
            seconds = parseInt(date, offset, offset += 2);
            if (seconds > 59 && seconds < 63) seconds = 59;
            if (checkOffset(date, offset, '.')) {
              offset++;
              int endOffset = offset;
              while (endOffset < date.length() && Character.isDigit(date.charAt(endOffset))) {
                endOffset++;
              }
              int parseEnd = Math.min(endOffset, offset + 3);
              int fraction = parseInt(date, offset, parseEnd);
              switch (parseEnd - offset) {
                case 2:
                  milliseconds = fraction * 10;
                  break;
                case 1:
                  milliseconds = fraction * 100;
                  break;
                default:
                  milliseconds = fraction;
              }
              offset = endOffset;
            }
          }
        }
      }

      if (date.length() <= offset) {
        throw new IllegalArgumentException("No time zone indicator");
      }

      TimeZone timezone;
      char tzIndicator = date.charAt(offset);
      if (tzIndicator == 'Z') {
        timezone = TIMEZONE_UTC;
      } else if (tzIndicator == '+' || tzIndicator == '-') {
        String tzOffset = date.substring(offset);
        if (tzOffset.length() < 5) tzOffset = tzOffset + "00";
        if ("+0000".equals(tzOffset) || "+00:00".equals(tzOffset)) {
          timezone = TIMEZONE_UTC;
        } else {
          timezone = TimeZone.getTimeZone("GMT" + tzOffset);
        }
      } else {
        throw new IllegalArgumentException("Invalid time zone indicator '" + tzIndicator + "'");
      }

      Calendar calendar = new GregorianCalendar(timezone);
      calendar.setLenient(false);
      calendar.set(Calendar.YEAR, year);
      calendar.set(Calendar.MONTH, month - 1);
      calendar.set(Calendar.DAY_OF_MONTH, day);
      calendar.set(Calendar.HOUR_OF_DAY, hour);
      calendar.set(Calendar.MINUTE, minutes);
      calendar.set(Calendar.SECOND, seconds);
      calendar.set(Calendar.MILLISECOND, milliseconds);

      return calendar.getTime();
    }

    private static boolean checkOffset(String value, int offset, char expected) {
      return offset < value.length() && value.charAt(offset) == expected;
    }

    private static int parseInt(String value, int beginIndex, int endIndex) {
      if (beginIndex < 0 || endIndex > value.length() || beginIndex > endIndex) {
        throw new NumberFormatException(value);
      }
      int i = beginIndex;
      int result = 0;
      int digit;
      if (i < endIndex) {
        digit = Character.digit(value.charAt(i++), 10);
        if (digit < 0) {
          throw new NumberFormatException(
              "Invalid number: " + value.substring(beginIndex, endIndex));
        }
        result = -digit;
      }
      while (i < endIndex) {
        digit = Character.digit(value.charAt(i++), 10);
        if (digit < 0) {
          throw new NumberFormatException(
              "Invalid number: " + value.substring(beginIndex, endIndex));
        }
        result *= 10;
        result -= digit;
      }
      return -result;
    }

    private static void padInt(StringBuilder buffer, int value, int length) {
      String strValue = Integer.toString(value);
      for (int i = length - strValue.length(); i > 0; i--) {
        buffer.append('0');
      }
      buffer.append(strValue);
    }
  }

  // endregion
}
