package io.sentry;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/** Utilities to deal with dates */
class DateUtils {
  /**
   * Get date formatted as expected by Sentry.
   *
   * @param date
   * @return the ISO formatted UTC date.
   */
  public static String getTimestampIsoFormat(Date date) {
    TimeZone tz = TimeZone.getTimeZone("UTC");
    DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    df.setTimeZone(tz);
    return df.format(date);
  }
}
