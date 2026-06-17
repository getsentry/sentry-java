package io.sentry.sqlite

import io.sentry.DateUtils
import io.sentry.ISpan
import io.sentry.SentryLongDate
import io.sentry.SentryNanotimeDate
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ComputeNanoStartTimestampForChildTest {

  @Test
  fun `returns parent wall clock plus elapsed monotonic time since parent started`() {
    val wallClockMillis = 1_000_000L
    val elapsedNanos = 500_000L
    val parentMonotonicNanos = System.nanoTime() - elapsedNanos
    val span = spanWithNanotimeStart(wallClockMillis, parentMonotonicNanos)

    val timestamp = span.computeNanoStartTimestampForChild()!!

    val elapsedSinceParentStart = timestamp - DateUtils.millisToNanos(wallClockMillis)
    assertTrue(elapsedSinceParentStart >= elapsedNanos)
    assertTrue(elapsedSinceParentStart < elapsedNanos + TEST_SLACK_NANOS)
  }

  @Test
  fun `same millisecond wall clocks with different monotonic offsets produce distinct ordered timestamps`() {
    val wallClockMillis = 1_000_000L
    val wallClockNanos = DateUtils.millisToNanos(wallClockMillis)
    val earlierParentMonotonicNanos = System.nanoTime() - 200_000L
    val laterParentMonotonicNanos = System.nanoTime() - 800_000L
    val earlierSpan = spanWithNanotimeStart(wallClockMillis, earlierParentMonotonicNanos)
    val laterSpan = spanWithNanotimeStart(wallClockMillis, laterParentMonotonicNanos)

    assertEquals(
      earlierSpan.startDate.nanoTimestamp(),
      laterSpan.startDate.nanoTimestamp(),
      "Raw parent timestamps share the same ms-quantized value",
    )

    val earlier = earlierSpan.computeNanoStartTimestampForChild()!!
    val later = laterSpan.computeNanoStartTimestampForChild()!!

    assertTrue(earlier > wallClockNanos)
    assertTrue(later > wallClockNanos)
    assertTrue(earlier < later)
    assertTrue(later - earlier >= 500_000L)
  }

  @Test
  fun `returns parent wall clock when no monotonic time has elapsed since parent started`() {
    val wallClockMillis = 1_000_000L
    val parentMonotonicNanos = System.nanoTime()
    val span = spanWithNanotimeStart(wallClockMillis, parentMonotonicNanos)

    val elapsedSinceParentStart =
      span.computeNanoStartTimestampForChild()!! - DateUtils.millisToNanos(wallClockMillis)
    assertTrue(elapsedSinceParentStart >= 0L)
    assertTrue(elapsedSinceParentStart < TEST_SLACK_NANOS)
  }

  @Test
  fun `works when parent wall clock differs from millisecond baseline`() {
    val wallClockMillis = 1_000_001L
    val elapsedNanos = 1_500_000L
    val parentMonotonicNanos = System.nanoTime() - elapsedNanos
    val span = spanWithNanotimeStart(wallClockMillis, parentMonotonicNanos)

    val elapsedSinceParentStart =
      span.computeNanoStartTimestampForChild()!! - DateUtils.millisToNanos(wallClockMillis)
    assertTrue(elapsedSinceParentStart >= elapsedNanos)
    assertTrue(elapsedSinceParentStart < elapsedNanos + TEST_SLACK_NANOS)
  }

  @Test
  fun `returns null when start date is not SentryNanotimeDate`() {
    val span = mock<ISpan>()
    whenever(span.startDate).thenReturn(SentryLongDate(DateUtils.millisToNanos(1_000_000L)))

    assertNull(span.computeNanoStartTimestampForChild())
  }

  private fun spanWithNanotimeStart(wallClockMillis: Long, parentMonotonicNanos: Long): ISpan {
    val startDate = SentryNanotimeDate(Date(wallClockMillis), parentMonotonicNanos)
    val span = mock<ISpan>()
    whenever(span.startDate).thenReturn(startDate)
    return span
  }

  companion object {

    // Upper bound for monotonic drift while the test body runs.
    private const val TEST_SLACK_NANOS = 50_000_000L
  }
}
