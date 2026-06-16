package io.sentry.sqlite

import io.sentry.DateUtils
import io.sentry.SentryLongDate
import io.sentry.SentryNanotimeDate
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class RepairPrecisionTest {

  @Test
  fun `repairs timestamp precision using nanotime diff from anchor`() {
    val anchor = SentryNanotimeDate(Date(1_000_000L), 100_000_000L)
    val date = SentryNanotimeDate(Date(1_000_000L), 100_500_000L)

    val repaired = date.repairPrecision(anchor)

    assertIs<SentryLongDate>(repaired)
    val anchorNanoTimestamp = DateUtils.millisToNanos(1_000_000L)
    assertEquals(anchorNanoTimestamp + 500_000L, repaired.nanoTimestamp())
  }

  @Test
  fun `two dates in the same millisecond produce distinct ordered timestamps`() {
    val anchor = SentryNanotimeDate(Date(1_000_000L), 100_000_000L)
    val earlier = SentryNanotimeDate(Date(1_000_000L), 100_200_000L)
    val later = SentryNanotimeDate(Date(1_000_000L), 100_800_000L)

    assertEquals(
      earlier.nanoTimestamp(),
      later.nanoTimestamp(),
      "Raw timestamps share the same ms-quantized value",
    )

    val anchorNanoTimestamp = DateUtils.millisToNanos(1_000_000L)
    val repairedEarlier = earlier.repairPrecision(anchor)
    val repairedLater = later.repairPrecision(anchor)
    assertEquals(anchorNanoTimestamp + 200_000L, repairedEarlier.nanoTimestamp())
    assertEquals(anchorNanoTimestamp + 800_000L, repairedLater.nanoTimestamp())
  }

  @Test
  fun `returns anchor nano timestamp when date matches anchor nanos`() {
    val anchor = SentryNanotimeDate(Date(1_000_000L), 100_000_000L)
    val date = SentryNanotimeDate(Date(1_000_000L), 100_000_000L)

    val repaired = date.repairPrecision(anchor)

    assertIs<SentryLongDate>(repaired)
    assertEquals(anchor.nanoTimestamp(), repaired.nanoTimestamp())
  }

  @Test
  fun `repairs SentryLongDate receiver to anchor millisecond baseline`() {
    val anchor = SentryNanotimeDate(Date(1_000_000L), 100_000_000L)
    val date = SentryLongDate(DateUtils.millisToNanos(1_000_000L))

    val repaired = date.repairPrecision(anchor)

    assertIs<SentryLongDate>(repaired)
    assertEquals(anchor.nanoTimestamp(), repaired.nanoTimestamp())
  }

  @Test
  fun `works when date and anchor span have different wall clock times`() {
    val anchor = SentryNanotimeDate(Date(1_000_000L), 100_000_000L)
    val date = SentryNanotimeDate(Date(1_000_001L), 101_500_000L)

    val repaired = date.repairPrecision(anchor)

    val anchorNanoTimestamp = DateUtils.millisToNanos(1_000_000L)
    assertEquals(anchorNanoTimestamp + 1_500_000L, repaired.nanoTimestamp())
  }

  @Test
  fun `returns self when anchor is not SentryNanotimeDate`() {
    val date = SentryNanotimeDate(Date(1_000_000L), 100_000_000L)
    val anchor = SentryLongDate(DateUtils.millisToNanos(1_000_000L))
    assertSame(date, date.repairPrecision(anchor = anchor))
  }

  @Test
  fun `returns self when anchor is null`() {
    val date = SentryNanotimeDate(Date(1_000_000L), 100_000_000L)
    assertSame(date, date.repairPrecision(anchor = null))
  }
}
