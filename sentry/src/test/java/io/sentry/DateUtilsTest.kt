package io.sentry

import io.sentry.vendor.gson.internal.bind.util.ISO8601Utils
import java.text.ParsePosition
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
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
  fun `When ISO date has offset`() {
    val input =
      mapOf(
        "2020-03-27T10:52:58.015+02:00" to "2020-03-27T08:52:58.015Z",
        "2020-03-27T10:52:58.015+0200" to "2020-03-27T08:52:58.015Z",
        "2020-03-27T10:52:58.015+02" to "2020-03-27T08:52:58.015Z",
        "2020-03-27T05:52:58.015-03:00" to "2020-03-27T08:52:58.015Z",
      )

    input.forEach {
      val timestamp = convertDate(DateUtils.getDateTime(it.key)).format(isoFormat)

      assertEquals(it.value, timestamp)
    }
  }

  @Test
  fun `When ISO date uses compact separators`() {
    val date = DateUtils.getDateTime("20200327T085258.015Z")

    val utcDate = convertDate(date)
    val timestamp = utcDate.format(isoFormat)

    assertEquals("2020-03-27T08:52:58.015Z", timestamp)
  }

  @Test
  fun `When ISO date has short fraction`() {
    val input =
      mapOf(
        "2020-03-27T08:52:58.1Z" to "2020-03-27T08:52:58.100Z",
        "2020-03-27T08:52:58.12Z" to "2020-03-27T08:52:58.120Z",
        "2020-03-27T08:52:58.123456Z" to "2020-03-27T08:52:58.123Z",
      )

    input.forEach {
      val timestamp = convertDate(DateUtils.getDateTime(it.key)).format(isoFormat)

      assertEquals(it.value, timestamp)
    }
  }

  @Test
  fun `When ISO date is invalid`() {
    assertFailsWith<IllegalArgumentException> { DateUtils.getDateTime("2020-02-30T08:52:58Z") }
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
  fun `Formats millis to ISO 8601 timestamp`() {
    val input =
      mapOf(
        Instant.parse("1970-01-01T00:00:00.000Z").toEpochMilli() to "1970-01-01T00:00:00.000Z",
        Instant.parse("1969-12-31T23:59:59.999Z").toEpochMilli() to "1969-12-31T23:59:59.999Z",
        Instant.parse("2000-02-29T12:34:56.789Z").toEpochMilli() to "2000-02-29T12:34:56.789Z",
        Instant.parse("1900-03-01T00:00:00.000Z").toEpochMilli() to "1900-03-01T00:00:00.000Z",
        Instant.parse("2100-03-01T00:00:00.000Z").toEpochMilli() to "2100-03-01T00:00:00.000Z",
        Instant.parse("2400-02-29T23:59:59.999Z").toEpochMilli() to "2400-02-29T23:59:59.999Z",
      )

    input.forEach { assertEquals(it.value, DateUtils.getTimestampFromMillis(it.key)) }
  }

  @Test
  fun `Fast timestamp formatter matches previous ISO8601 formatter`() {
    val input =
      listOf(
        "1582-10-04T00:00:00.000Z",
        "1582-10-15T00:00:00.000Z",
        "1900-03-01T00:00:00.000Z",
        "1969-12-31T23:59:59.999Z",
        "1970-01-01T00:00:00.000Z",
        "1999-12-31T23:59:59.999Z",
        "2000-02-29T12:34:56.789Z",
        "2020-03-27T08:52:58.015Z",
        "2024-02-29T23:59:59.001Z",
        "2100-03-01T00:00:00.000Z",
        "2400-02-29T23:59:59.999Z",
      )

    input
      .map { ISO8601Utils.parse(it, ParsePosition(0)).time }
      .forEach {
        assertEquals(
          ISO8601Utils.format(Date(it), true),
          DateUtils.getTimestampFromMillis(it),
          "millis=$it",
        )
      }
  }

  @Test
  fun `Fast timestamp parser matches previous ISO8601 parser`() {
    val input =
      listOf(
        "2020-03-27T08:52Z",
        "2020-03-27T08:52:58Z",
        "2020-03-27T08:52:58.015Z",
        "20200327T085258.015Z",
        "2020-03-27T10:52:58.015+02:00",
        "2020-03-27T10:52:58.015+0200",
        "2020-03-27T10:52:58.015+02",
        "2020-03-27T05:52:58.015-03:00",
        "2020-03-27T05:22:58.015-0330",
        "2020-03-27T08:52:58.1Z",
        "2020-03-27T08:52:58.12Z",
        "2020-03-27T08:52:58.123456Z",
        "2020-03-27T08:52:58Ztrailing",
        "2016-12-31T23:59:60Z",
        "1582-10-04T00:00:00.000Z",
        "1582-10-15T00:00:00.000Z",
        "1900-03-01T00:00:00.000Z",
        "2000-02-29T12:34:56.789Z",
        "2100-03-01T00:00:00.000Z",
      )

    input.forEach {
      assertEquals(
        ISO8601Utils.parse(it, ParsePosition(0)).time,
        DateUtils.getDateTime(it).time,
        "timestamp=$it",
      )
    }
  }

  @Test
  fun `Fast timestamp parser matches previous ISO8601 parser for date-only values`() {
    withDefaultTimeZone("America/Los_Angeles") {
      val input = listOf("2020-03-27", "20200327", "2020-02-30")

      input.forEach {
        assertEquals(
          ISO8601Utils.parse(it, ParsePosition(0)).time,
          DateUtils.getDateTime(it).time,
          "timestamp=$it",
        )
      }
    }
  }

  @Test
  fun `Fast timestamp parser matches previous ISO8601 parser for date-only values with timezone`() {
    val input =
      listOf(
        "2020-03-27Z",
        "2020-03-27+02:00",
        "2020-03-27+0200",
        "2020-03-27+02",
        "2020-03-27-03:30",
        "20200327Z",
        "20200327+02:00",
        "20200327-0330",
      )

    input.forEach {
      assertEquals(
        ISO8601Utils.parse(it, ParsePosition(0)).time,
        DateUtils.getDateTime(it).time,
        "timestamp=$it",
      )
    }
  }

  @Test
  fun `Fast timestamp parser rejects invalid date-only values with timezone like previous ISO8601 parser`() {
    val timestamp = "2020-02-30Z"

    assertFailsWith<Exception> { ISO8601Utils.parse(timestamp, ParsePosition(0)) }
    assertFailsWith<IllegalArgumentException> { DateUtils.getDateTime(timestamp) }
  }

  @Test
  fun `Fast timestamp parser rejects date-time without timezone like previous ISO8601 parser`() {
    val input = listOf("2020-03-27T08:52", "2020-03-27T08:52:58", "2020-03-27T08:52:58.015")

    input.forEach {
      assertFailsWith<Exception>("timestamp=$it") { ISO8601Utils.parse(it, ParsePosition(0)) }
      assertFailsWith<IllegalArgumentException>("timestamp=$it") { DateUtils.getDateTime(it) }
    }
  }

  @Test
  fun `Fast timestamp parser rejects Gregorian cutover gap like previous ISO8601 parser`() {
    val timestamp = "1582-10-10T00:00:00.000Z"

    assertFailsWith<Exception> { ISO8601Utils.parse(timestamp, ParsePosition(0)) }
    assertFailsWith<IllegalArgumentException> { DateUtils.getDateTime(timestamp) }
  }

  @Test
  fun `Millis formats to Date`() {
    val millis = 1591533492L * 1000L + 631
    val actual = DateUtils.getDateTime(millis)

    val utcActual = convertDate(actual)
    val timestamp = utcActual.format(isoFormat)

    assertEquals(millis, actual.time)
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

  private fun withDefaultTimeZone(timeZoneId: String, block: () -> Unit) {
    val previousTimeZone = TimeZone.getDefault()
    try {
      TimeZone.setDefault(TimeZone.getTimeZone(timeZoneId))
      block()
    } finally {
      TimeZone.setDefault(previousTimeZone)
    }
  }

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
