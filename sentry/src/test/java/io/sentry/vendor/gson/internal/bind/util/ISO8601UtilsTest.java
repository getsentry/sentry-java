package io.sentry.vendor.gson.internal.bind.util;

// Source: https://github.com/google/gson
// Tag: gson-parent-2.8.9
// Commit Hash: 6a368d89da37917be7714c3072b8378f4120110a
// Changes: --

import org.junit.Test;

import java.text.ParseException;
import java.text.ParsePosition;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ISO8601UtilsTest {

  private static TimeZone utcTimeZone() {
    return TimeZone.getTimeZone("UTC");
  }

  private static GregorianCalendar createUtcCalendar() {
    TimeZone utc = utcTimeZone();
    GregorianCalendar calendar = new GregorianCalendar(utc);
    // Calendar was created with current time, must clear it
    calendar.clear();
    return calendar;
  }

  @Test
  public void testDateFormatString() {
    GregorianCalendar calendar = new GregorianCalendar(utcTimeZone(), Locale.US);
    // Calendar was created with current time, must clear it
    calendar.clear();
    calendar.set(2018, Calendar.JUNE, 25);
    Date date = calendar.getTime();
    String dateStr = ISO8601Utils.format(date);
    String expectedDate = "2018-06-25";
    assertEquals(expectedDate, dateStr.substring(0, expectedDate.length()));
  }

  @Test
  public void testDateFormatWithMilliseconds() {
    long time = 1530209176870L;
    Date date = new Date(time);
    String dateStr = ISO8601Utils.format(date, true);
    String expectedDate = "2018-06-28T18:06:16.870Z";
    assertEquals(expectedDate, dateStr);
  }

  @Test
  public void testDateFormatWithTimezone() {
    long time = 1530209176870L;
    Date date = new Date(time);
    String dateStr = ISO8601Utils.format(date, true, TimeZone.getTimeZone("Brazil/East"));
    String expectedDate = "2018-06-28T15:06:16.870-03:00";
    assertEquals(expectedDate, dateStr);
  }

  @Test
  public void testDateParseWithDefaultTimezone() throws ParseException {
    String dateStr = "2018-06-25";
    Date date = ISO8601Utils.parse(dateStr, new ParsePosition(0));
    Date expectedDate = new GregorianCalendar(2018, Calendar.JUNE, 25).getTime();
    assertEquals(expectedDate, date);
  }

  @Test
  public void testDateParseWithTimezone() throws ParseException {
    String dateStr = "2018-06-25T00:00:00-03:00";
    Date date = ISO8601Utils.parse(dateStr, new ParsePosition(0));
    GregorianCalendar calendar = createUtcCalendar();
    calendar.set(2018, Calendar.JUNE, 25, 3, 0);
    Date expectedDate = calendar.getTime();
    assertEquals(expectedDate, date);
  }

  @Test
  public void testDateParseSpecialTimezone() throws ParseException {
    String dateStr = "2018-06-25T00:02:00-02:58";
    Date date = ISO8601Utils.parse(dateStr, new ParsePosition(0));
    GregorianCalendar calendar = createUtcCalendar();
    calendar.set(2018, Calendar.JUNE, 25, 3, 0);
    Date expectedDate = calendar.getTime();
    assertEquals(expectedDate, date);
  }

  @Test
  public void testDateParseInvalidTime() {
    String dateStr = "2018-06-25T61:60:62-03:00";
    boolean thrown = false;
    try {
      ISO8601Utils.parse(dateStr, new ParsePosition(0));
    } catch (ParseException e) {
      thrown = true;
    }
    assertTrue("Expected to throw a ParseException, but failed.", thrown);
  }
}

