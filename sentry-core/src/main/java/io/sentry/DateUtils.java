package io.sentry;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/** Utilities to deal with dates */
class DateUtils {
  private static final String UTC = "UTC";
  private static final String UTC_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";

  /**
   * Get date formatted as expected by Sentry.
   *
   * @param date
   * @return the ISO formatted UTC date.
   */
  public static String getTimestampIsoFormat(Date date) {
    TimeZone tz = TimeZone.getTimeZone(UTC);
    DateFormat df = new SimpleDateFormat(UTC_FORMAT);
    df.setTimeZone(tz);
    return df.format(date);
  }

  /**
   * Get the current date and time as ISO UTC
   *
   * @return the ISO UTC date and time
   */
  public static Date getCurrentDateTime() {
    TimeZone tz = TimeZone.getTimeZone(UTC);
    return Calendar.getInstance(tz).getTime();
  }
}
