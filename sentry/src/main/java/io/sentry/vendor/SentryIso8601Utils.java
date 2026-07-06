// Civil date conversion algorithms adapted from Howard Hinnant's date algorithms.
// Placed in the public domain by Howard Hinnant.
// https://howardhinnant.github.io/date_algorithms.html

package io.sentry.vendor;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.SimpleTimeZone;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class SentryIso8601Utils {

  private static final long MILLIS_PER_SECOND = 1000L;
  private static final long MILLIS_PER_MINUTE = 60L * MILLIS_PER_SECOND;
  private static final long MILLIS_PER_HOUR = 60L * MILLIS_PER_MINUTE;
  private static final long MILLIS_PER_DAY = 24L * MILLIS_PER_HOUR;
  private static final long GREGORIAN_CUTOVER_MILLIS = -12219292800000L;
  private static final int DAYS_0000_TO_1970 = 719468;

  private SentryIso8601Utils() {}

  public static long parseTimestamp(final @NotNull String timestamp) {
    final int length = timestamp.length();
    int offset = 0;

    final int year = parseInt(timestamp, offset, offset += 4);
    if (checkOffset(timestamp, offset, '-')) {
      offset++;
    }

    final int month = parseInt(timestamp, offset, offset += 2);
    if (checkOffset(timestamp, offset, '-')) {
      offset++;
    }

    final int day = parseInt(timestamp, offset, offset += 2);

    if (!checkOffset(timestamp, offset, 'T')) {
      if (offset == length) {
        return dateOnlyEpochMillis(year, month, day);
      }
      final char timezoneIndicator = timestamp.charAt(offset);
      if (timezoneIndicator == 'Z' || timezoneIndicator == '+' || timezoneIndicator == '-') {
        return dateOnlyEpochMillisWithTimezone(timestamp, length, offset, year, month, day);
      }
      throw new IllegalArgumentException("Invalid date separator");
    }
    validateDate(year, month, day);
    offset++;

    final int hour = parseInt(timestamp, offset, offset += 2);
    if (checkOffset(timestamp, offset, ':')) {
      offset++;
    }

    final int minute = parseInt(timestamp, offset, offset += 2);
    if (checkOffset(timestamp, offset, ':')) {
      offset++;
    }

    int second = 0;
    int millisecond = 0;
    if (length > offset) {
      final char c = timestamp.charAt(offset);
      if (c != 'Z' && c != '+' && c != '-') {
        second = parseInt(timestamp, offset, offset += 2);
        if (second > 59 && second < 63) {
          second = 59;
        }
        if (checkOffset(timestamp, offset, '.')) {
          offset++;
          final int endOffset = indexOfNonDigit(timestamp, offset);
          if (endOffset == offset) {
            throw new IllegalArgumentException("Missing millisecond digits");
          }
          final int parseEndOffset = Math.min(endOffset, offset + 3);
          final int fraction = parseInt(timestamp, offset, parseEndOffset);
          switch (parseEndOffset - offset) {
            case 1:
              millisecond = fraction * 100;
              break;
            case 2:
              millisecond = fraction * 10;
              break;
            default:
              millisecond = fraction;
              break;
          }
          offset = endOffset;
        }
      }
    }
    validateTime(hour, minute, second, millisecond);

    if (length <= offset) {
      throw new IllegalArgumentException("No time zone indicator");
    }

    final int timezoneOffsetMillis;
    final boolean allowTrailingCharacters;
    final char timezoneIndicator = timestamp.charAt(offset);
    if (timezoneIndicator == 'Z') {
      timezoneOffsetMillis = 0;
      offset++;
      allowTrailingCharacters = true;
    } else if (timezoneIndicator == '+' || timezoneIndicator == '-') {
      final int sign = timezoneIndicator == '+' ? 1 : -1;
      offset++;
      final int timezoneHour = parseInt(timestamp, offset, offset += 2);
      int timezoneMinute = 0;
      if (checkOffset(timestamp, offset, ':')) {
        offset++;
      }
      if (length >= offset + 2) {
        timezoneMinute = parseInt(timestamp, offset, offset += 2);
      }
      validateTimezone(timezoneHour, timezoneMinute);
      timezoneOffsetMillis =
          sign * (int) (timezoneHour * MILLIS_PER_HOUR + timezoneMinute * MILLIS_PER_MINUTE);
      allowTrailingCharacters = false;
    } else {
      throw new IllegalArgumentException("Invalid time zone indicator");
    }

    if (!allowTrailingCharacters && offset != length) {
      throw new IllegalArgumentException("Invalid trailing characters");
    }

    if (isBeforeGregorianCutover(year, month, day)) {
      return epochMillisWithCalendar(
          year, month, day, hour, minute, second, millisecond, timezoneOffsetMillis);
    }

    return epochMillis(year, month, day, hour, minute, second, millisecond, timezoneOffsetMillis);
  }

  public static @NotNull String formatTimestamp(final long millis) {
    if (millis < GREGORIAN_CUTOVER_MILLIS) {
      return formatTimestampWithCalendar(millis);
    }

    final long epochDay = Math.floorDiv(millis, MILLIS_PER_DAY);
    int millisOfDay = (int) Math.floorMod(millis, MILLIS_PER_DAY);

    final int[] yearMonthDay = epochDayToYearMonthDay(epochDay);
    final int hour = millisOfDay / (int) MILLIS_PER_HOUR;
    millisOfDay -= hour * (int) MILLIS_PER_HOUR;
    final int minute = millisOfDay / (int) MILLIS_PER_MINUTE;
    millisOfDay -= minute * (int) MILLIS_PER_MINUTE;
    final int second = millisOfDay / (int) MILLIS_PER_SECOND;
    final int millisecond = millisOfDay - second * (int) MILLIS_PER_SECOND;

    final StringBuilder timestamp = new StringBuilder("yyyy-MM-ddThh:mm:ss.sssZ".length());
    padInt(timestamp, yearMonthDay[0], "yyyy".length());
    timestamp.append('-');
    padInt(timestamp, yearMonthDay[1], "MM".length());
    timestamp.append('-');
    padInt(timestamp, yearMonthDay[2], "dd".length());
    timestamp.append('T');
    padInt(timestamp, hour, "hh".length());
    timestamp.append(':');
    padInt(timestamp, minute, "mm".length());
    timestamp.append(':');
    padInt(timestamp, second, "ss".length());
    timestamp.append('.');
    padInt(timestamp, millisecond, "sss".length());
    timestamp.append('Z');
    return timestamp.toString();
  }

  private static long dateOnlyEpochMillis(final int year, final int month, final int day) {
    return new GregorianCalendar(year, month - 1, day).getTimeInMillis();
  }

  private static long dateOnlyEpochMillisWithTimezone(
      final @NotNull String timestamp,
      final int length,
      int offset,
      final int year,
      final int month,
      final int day) {
    final int timezoneOffsetMillis;
    final boolean allowTrailingCharacters;
    final char timezoneIndicator = timestamp.charAt(offset);
    if (timezoneIndicator == 'Z') {
      timezoneOffsetMillis = 0;
      offset++;
      allowTrailingCharacters = true;
    } else if (timezoneIndicator == '+' || timezoneIndicator == '-') {
      final int sign = timezoneIndicator == '+' ? 1 : -1;
      offset++;
      final int timezoneHour = parseInt(timestamp, offset, offset += 2);
      int timezoneMinute = 0;
      if (checkOffset(timestamp, offset, ':')) {
        offset++;
      }
      if (length >= offset + 2) {
        timezoneMinute = parseInt(timestamp, offset, offset += 2);
      }
      validateTimezone(timezoneHour, timezoneMinute);
      timezoneOffsetMillis =
          sign * (int) (timezoneHour * MILLIS_PER_HOUR + timezoneMinute * MILLIS_PER_MINUTE);
      allowTrailingCharacters = false;
    } else {
      throw new IllegalArgumentException("Invalid time zone indicator");
    }

    if (!allowTrailingCharacters && offset != length) {
      throw new IllegalArgumentException("Invalid trailing characters");
    }

    if (isBeforeGregorianCutover(year, month, day)) {
      return epochMillisWithCalendar(year, month, day, 0, 0, 0, 0, timezoneOffsetMillis);
    }
    validateDate(year, month, day);
    return epochMillis(year, month, day, 0, 0, 0, 0, timezoneOffsetMillis);
  }

  private static long epochMillisWithCalendar(
      final int year,
      final int month,
      final int day,
      final int hour,
      final int minute,
      final int second,
      final int millisecond,
      final int timezoneOffsetMillis) {
    final GregorianCalendar calendar = new GregorianCalendar(new SimpleTimeZone(timezoneOffsetMillis, "GMT"));
    calendar.setLenient(false);
    calendar.set(Calendar.YEAR, year);
    calendar.set(Calendar.MONTH, month - 1);
    calendar.set(Calendar.DAY_OF_MONTH, day);
    calendar.set(Calendar.HOUR_OF_DAY, hour);
    calendar.set(Calendar.MINUTE, minute);
    calendar.set(Calendar.SECOND, second);
    calendar.set(Calendar.MILLISECOND, millisecond);
    return calendar.getTimeInMillis();
  }

  private static @NotNull String formatTimestampWithCalendar(final long millis) {
    final GregorianCalendar calendar = new GregorianCalendar(new SimpleTimeZone(0, "UTC"));
    calendar.setTimeInMillis(millis);

    final StringBuilder timestamp = new StringBuilder("yyyy-MM-ddThh:mm:ss.sssZ".length());
    padInt(timestamp, calendar.get(Calendar.YEAR), "yyyy".length());
    timestamp.append('-');
    padInt(timestamp, calendar.get(Calendar.MONTH) + 1, "MM".length());
    timestamp.append('-');
    padInt(timestamp, calendar.get(Calendar.DAY_OF_MONTH), "dd".length());
    timestamp.append('T');
    padInt(timestamp, calendar.get(Calendar.HOUR_OF_DAY), "hh".length());
    timestamp.append(':');
    padInt(timestamp, calendar.get(Calendar.MINUTE), "mm".length());
    timestamp.append(':');
    padInt(timestamp, calendar.get(Calendar.SECOND), "ss".length());
    timestamp.append('.');
    padInt(timestamp, calendar.get(Calendar.MILLISECOND), "sss".length());
    timestamp.append('Z');
    return timestamp.toString();
  }

  private static long epochMillis(
      final int year,
      final int month,
      final int day,
      final int hour,
      final int minute,
      final int second,
      final int millisecond,
      final int timezoneOffsetMillis) {
    return daysFromYearMonthDay(year, month, day) * MILLIS_PER_DAY
        + hour * MILLIS_PER_HOUR
        + minute * MILLIS_PER_MINUTE
        + second * MILLIS_PER_SECOND
        + millisecond
        - timezoneOffsetMillis;
  }

  private static long daysFromYearMonthDay(int year, final int month, final int day) {
    year -= month <= 2 ? 1 : 0;
    final long era = Math.floorDiv(year, 400);
    final int yearOfEra = (int) (year - era * 400);
    final int dayOfYear = (153 * (month + (month > 2 ? -3 : 9)) + 2) / 5 + day - 1;
    final int dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear;
    return era * 146097 + dayOfEra - DAYS_0000_TO_1970;
  }

  private static int[] epochDayToYearMonthDay(long epochDay) {
    epochDay += DAYS_0000_TO_1970;
    final long era = Math.floorDiv(epochDay, 146097);
    final int dayOfEra = (int) (epochDay - era * 146097);
    final int yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36524 - dayOfEra / 146096) / 365;
    final int year = (int) (yearOfEra + era * 400);
    final int dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100);
    final int monthPrime = (5 * dayOfYear + 2) / 153;
    final int day = dayOfYear - (153 * monthPrime + 2) / 5 + 1;
    final int month = monthPrime < 10 ? monthPrime + 3 : monthPrime - 9;
    return new int[] {year + (month <= 2 ? 1 : 0), month, day};
  }

  private static boolean isBeforeGregorianCutover(final int year, final int month, final int day) {
    return year < 1582 || (year == 1582 && (month < 10 || (month == 10 && day < 15)));
  }

  private static void validateDate(final int year, final int month, final int day) {
    if (year < 1 || month < 1 || month > 12 || day < 1 || day > daysInMonth(year, month)) {
      throw new IllegalArgumentException("Invalid date");
    }
  }

  private static void validateTime(
      final int hour, final int minute, final int second, final int millisecond) {
    if (hour < 0
        || hour > 23
        || minute < 0
        || minute > 59
        || second < 0
        || second > 59
        || millisecond < 0
        || millisecond > 999) {
      throw new IllegalArgumentException("Invalid time");
    }
  }

  private static void validateTimezone(final int hour, final int minute) {
    if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
      throw new IllegalArgumentException("Invalid time zone");
    }
  }

  private static int daysInMonth(final int year, final int month) {
    switch (month) {
      case 2:
        return isLeapYear(year) ? 29 : 28;
      case 4:
      case 6:
      case 9:
      case 11:
        return 30;
      default:
        return 31;
    }
  }

  private static boolean isLeapYear(final int year) {
    return (year % 4 == 0) && (year % 100 != 0 || year % 400 == 0);
  }

  private static boolean checkOffset(
      final @NotNull String value, final int offset, final char expected) {
    return offset < value.length() && value.charAt(offset) == expected;
  }

  private static int parseInt(
      final @NotNull String value, final int beginIndex, final int endIndex) {
    if (beginIndex < 0 || endIndex > value.length() || beginIndex >= endIndex) {
      throw new NumberFormatException(value);
    }

    int result = 0;
    for (int i = beginIndex; i < endIndex; i++) {
      final char c = value.charAt(i);
      if (c < '0' || c > '9') {
        throw new NumberFormatException("Invalid number: " + value.substring(beginIndex, endIndex));
      }
      result = result * 10 + c - '0';
    }
    return result;
  }

  private static void padInt(
      final @NotNull StringBuilder buffer, final int value, final int length) {
    if (value < 0) {
      buffer.append('-');
      padInt(buffer, -value, length);
      return;
    }
    final String strValue = Integer.toString(value);
    for (int i = length - strValue.length(); i > 0; i--) {
      buffer.append('0');
    }
    buffer.append(strValue);
  }

  private static int indexOfNonDigit(final @NotNull String string, final int offset) {
    for (int i = offset; i < string.length(); i++) {
      final char c = string.charAt(i);
      if (c < '0' || c > '9') {
        return i;
      }
    }
    return string.length();
  }
}
