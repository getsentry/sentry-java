package io.sentry.core;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Utilities to deal with dates */
public final class DateUtils {
  private static final String UTC = "UTC";
  private static final String ISO_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";

  /**
   * Get date formatted as expected by Sentry.
   *
   * @param date the current date with local timezone
   * @return the ISO formatted UTC date.
   */
  public static String getTimestampIsoFormat(Date date) {
    TimeZone tz = TimeZone.getTimeZone(UTC);
    DateFormat df = new SimpleDateFormat(ISO_FORMAT, Locale.US);
    df.setTimeZone(tz);
    return df.format(date);
  }

  /**
   * Get the current date and time as ISO UTC
   *
   * @return the ISO UTC date and time
   */
  public static Date getCurrentDateTime() {
    String timestampIsoFormat = getTimestampIsoFormat(new Date());
    return getDateTime(timestampIsoFormat);
  }

  /**
   * Get date
   *
   * @param timestamp already UTC format
   * @return the Date
   */
  public static Date getDateTime(String timestamp) throws IllegalArgumentException {
    DateFormat df = new SimpleDateFormat(ISO_FORMAT, Locale.US);
    try {
      return df.parse(timestamp);
    } catch (ParseException e) {
      throw new IllegalArgumentException("timestamp is not ISO format " + timestamp);
    }
  }

  /**
   * Get date formatted as expected by Sentry.
   *
   * @param date already UTC format
   * @return the ISO formatted date.
   */
  public static String getTimestamp(Date date) {
    DateFormat df = new SimpleDateFormat(ISO_FORMAT, Locale.US);
    return df.format(date);
  }
}
