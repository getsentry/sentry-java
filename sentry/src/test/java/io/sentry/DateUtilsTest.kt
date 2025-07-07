package io.sentry

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
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
