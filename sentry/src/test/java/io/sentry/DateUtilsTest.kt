package io.sentry

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DateUtilsTest {
  private val utcTimeZone: ZoneId = ZoneId.of("UTC")
  private val isoFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

  @Test
  fun `When ISO date has milliseconds`() {
    val date = DateUtils.getDateTime("2020-03-27T08:52:58.015Z")

    val utcDate = convertDate(date)
    val timestamp = utcDate.format(isoFormat)

    assertEquals("2020-03-27T08:52:58.015Z", timestamp)
  }

  @Test
  fun `When ISO date has only seconds`() {
    val date = DateUtils.getDateTime("2020-03-27T08:52:58Z")

    val utcDate = convertDate(date)
    val timestamp = utcDate.format(isoFormat)

    assertEquals("2020-03-27T08:52:58.000Z", timestamp)
  }

  @Test
  fun `Converts from Date to ISO 8601 and back to Date`() {
    val currentDate = DateUtils.getCurrentDateTime()
    val currentDateISO = DateUtils.getTimestamp(currentDate)
    val currentDate2 = DateUtils.getDateTime(currentDateISO)
    val currentDateISO2 = DateUtils.getTimestamp(currentDate2)

    assertEquals(currentDateISO, currentDateISO2)
    assertEquals(currentDate, currentDate2)
  }

  @Test
  fun `Millis timestamp with millis precision, it should be UTC`() {
    val input =
      listOf(
        Pair("1591533492.631", "2020-06-07T12:38:12.631Z"),
        Pair("1591533492.63", "2020-06-07T12:38:12.630Z"),
        Pair("1591533492.6", "2020-06-07T12:38:12.600Z"),
        Pair("1591533492", "2020-06-07T12:38:12.000Z"),
        Pair("1591533492.631631", "2020-06-07T12:38:12.631Z"),
        Pair("1591533492.999999", "2020-06-07T12:38:12.999Z"),
      )

    input.forEach {
      val actual = DateUtils.getDateTimeWithMillisPrecision(it.first)

      val utcActual = convertDate(actual)
      val timestamp = utcActual.format(isoFormat)

      assertEquals(it.second, timestamp)
    }
  }

  @Test
  fun `getCurrentDateTime returns UTC date`() {
    val currentDate = DateUtils.getCurrentDateTime()
    val utcCurrentDate = convertDate(currentDate)

    val utcDate = LocalDateTime.now(utcTimeZone)

    assertTrue { utcCurrentDate.plusSeconds(1).isAfter(utcDate) }
    assertTrue { utcCurrentDate.minusSeconds(1).isBefore(utcDate) }
  }

  @Test
  fun `Millis formats to Date`() {
    val millis = 1591533492L * 1000L + 631
    val actual = DateUtils.getDateTime(millis)

    val utcActual = convertDate(actual)
    val timestamp = utcActual.format(isoFormat)

    assertEquals("2020-06-07T12:38:12.631Z", timestamp)
  }

  @Test
  fun `nanos can be converted to Date losing nano precision`() {
    val millis = 1591533492L * 1000L + 631L
    val nanos = (millis * 1000L * 1000L) + (427L * 1000L)
    val date = DateUtils.nanosToDate(nanos)
    assertEquals(millis, date.time)
  }

  @Test
  fun `nanos can be converted to Date but rounds down to next ms`() {
    val millis = 1591533492L * 1000L + 631L
    val nanos = (millis * 1000L * 1000L) + (999L * 1000L)
    val date = DateUtils.nanosToDate(nanos)
    assertEquals(millis, date.time)
  }

  @Test
  fun `nanos can be 0`() {
    val date = DateUtils.nanosToDate(0)
    assertEquals(0, date.time)
  }

  @Test
  fun `nanos can be converted to seconds`() {
    val seconds = DateUtils.nanosToSeconds(123456)
    assertClose(0.000123456, seconds)
  }

  @Test
  fun `format produces millis with Z suffix`() {
    val time = 1530209176870L
    val date = Date(time)
    val dateStr = DateUtils.getTimestamp(date)
    assertEquals("2018-06-28T18:06:16.870Z", dateStr)
  }

  @Test
  fun `parse with timezone offset`() {
    val date = DateUtils.getDateTime("2018-06-25T00:00:00-03:00")
    val calendar = createUtcCalendar()
    calendar.set(2018, Calendar.JUNE, 25, 3, 0)
    assertEquals(calendar.time, date)
  }

  @Test
  fun `parse with special timezone offset`() {
    val date = DateUtils.getDateTime("2018-06-25T00:02:00-02:58")
    val calendar = createUtcCalendar()
    calendar.set(2018, Calendar.JUNE, 25, 3, 0)
    assertEquals(calendar.time, date)
  }

  @Test
  fun `parse rejects invalid time`() {
    assertFailsWith<IllegalArgumentException> { DateUtils.getDateTime("2018-06-25T61:60:62-03:00") }
  }

  // region Legacy fallback tests

  @Test
  fun `legacy format produces millis with Z suffix`() {
    val time = 1530209176870L
    val date = Date(time)
    val dateStr = DateUtils.Iso8601Legacy.format(date)
    assertEquals("2018-06-28T18:06:16.870Z", dateStr)
  }

  @Test
  fun `legacy parse with Z timezone`() {
    val date = DateUtils.Iso8601Legacy.parse("2020-03-27T08:52:58.015Z")
    val utcDate = convertDate(date)
    val timestamp = utcDate.format(isoFormat)
    assertEquals("2020-03-27T08:52:58.015Z", timestamp)
  }

  @Test
  fun `legacy parse without millis`() {
    val date = DateUtils.Iso8601Legacy.parse("2020-03-27T08:52:58Z")
    val utcDate = convertDate(date)
    val timestamp = utcDate.format(isoFormat)
    assertEquals("2020-03-27T08:52:58.000Z", timestamp)
  }

  @Test
  fun `legacy parse with timezone offset`() {
    val date = DateUtils.Iso8601Legacy.parse("2018-06-25T00:00:00-03:00")
    val calendar = createUtcCalendar()
    calendar.set(2018, Calendar.JUNE, 25, 3, 0)
    assertEquals(calendar.time, date)
  }

  @Test
  fun `legacy parse date only`() {
    val date = DateUtils.Iso8601Legacy.parse("2018-06-25")
    val expected = GregorianCalendar(2018, Calendar.JUNE, 25).time
    assertEquals(expected, date)
  }

  @Test
  fun `legacy roundtrip`() {
    val original = DateUtils.getCurrentDateTime()
    val iso = DateUtils.Iso8601Legacy.format(original)
    val parsed = DateUtils.Iso8601Legacy.parse(iso)
    val iso2 = DateUtils.Iso8601Legacy.format(parsed)
    assertEquals(iso, iso2)
    assertEquals(original, parsed)
  }

  // endregion

  // region java.time tests

  @Test
  fun `java time format produces millis with Z suffix`() {
    assertTrue(DateUtils.HAS_JAVA_TIME, "java.time should be available on JVM")
    val time = 1530209176870L
    val date = Date(time)
    val dateStr = DateUtils.Iso8601JavaTime.format(date)
    assertEquals("2018-06-28T18:06:16.870Z", dateStr)
  }

  @Test
  fun `java time parse with Z timezone`() {
    val date = DateUtils.Iso8601JavaTime.parse("2020-03-27T08:52:58.015Z")
    val utcDate = convertDate(date)
    val timestamp = utcDate.format(isoFormat)
    assertEquals("2020-03-27T08:52:58.015Z", timestamp)
  }

  @Test
  fun `java time parse without millis`() {
    val date = DateUtils.Iso8601JavaTime.parse("2020-03-27T08:52:58Z")
    val utcDate = convertDate(date)
    val timestamp = utcDate.format(isoFormat)
    assertEquals("2020-03-27T08:52:58.000Z", timestamp)
  }

  @Test
  fun `java time parse with timezone offset`() {
    val date = DateUtils.Iso8601JavaTime.parse("2018-06-25T00:00:00-03:00")
    val calendar = createUtcCalendar()
    calendar.set(2018, Calendar.JUNE, 25, 3, 0)
    assertEquals(calendar.time, date)
  }

  @Test
  fun `java time roundtrip`() {
    val original = DateUtils.getCurrentDateTime()
    val iso = DateUtils.Iso8601JavaTime.format(original)
    val parsed = DateUtils.Iso8601JavaTime.parse(iso)
    val iso2 = DateUtils.Iso8601JavaTime.format(parsed)
    assertEquals(iso, iso2)
    assertEquals(original, parsed)
  }

  @Test
  fun `both implementations produce identical output`() {
    val dates =
      listOf(Date(0), Date(1530209176870L), Date(1591533492631L), DateUtils.getCurrentDateTime())
    dates.forEach { date ->
      val javaTime = DateUtils.Iso8601JavaTime.format(date)
      val legacy = DateUtils.Iso8601Legacy.format(date)
      assertEquals(legacy, javaTime, "Mismatch for date: $date")
    }
  }

  @Test
  fun `both implementations parse identically`() {
    val timestamps =
      listOf(
        "2020-03-27T08:52:58.015Z",
        "2020-03-27T08:52:58Z",
        "2018-06-25T00:00:00-03:00",
        "2018-06-25T00:02:00-02:58",
      )
    timestamps.forEach { ts ->
      val javaTime = DateUtils.Iso8601JavaTime.parse(ts)
      val legacy = DateUtils.Iso8601Legacy.parse(ts)
      assertEquals(legacy, javaTime, "Mismatch for timestamp: $ts")
    }
  }

  // endregion

  private fun createUtcCalendar(): GregorianCalendar {
    val utc = TimeZone.getTimeZone("UTC")
    val calendar = GregorianCalendar(utc)
    calendar.clear()
    return calendar
  }

  private fun convertDate(date: Date): LocalDateTime =
    Instant.ofEpochMilli(date.time).atZone(utcTimeZone).toLocalDateTime()

  private fun assertClose(expected: Double, actual: Double?) {
    assertNotNull(actual)
    val diff = Math.abs(expected - actual)
    val threshold = 0.000001
    if (diff > threshold) {
      throw RuntimeException(
        "Expected $actual to be within $threshold of $expected but was $diff off"
      )
    }
  }
}
