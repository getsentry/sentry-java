package io.sentry.sqlite

import io.sentry.DateUtils
import io.sentry.ISpan
import io.sentry.time.AnchoredClock
import io.sentry.time.EpochClock
import io.sentry.time.MonotonicClock
import io.sentry.time.Timestamp
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ComputeNanoStartTimestampForChildTest {

  @Test
  fun `returns the parent's own timeline projected to now`() {
    val clock = FakeClock()
    val span = anchoredSpan(WALL_CLOCK_MILLIS, clock)

    clock.advanceNanos(500_000L)

    assertEquals(
      DateUtils.millisToNanos(WALL_CLOCK_MILLIS) + 500_000L,
      span.computeNanoStartTimestampForChild(),
    )
  }

  @Test
  fun `returns the parent's start when no time has elapsed since it started`() {
    val span = anchoredSpan(WALL_CLOCK_MILLIS, FakeClock())

    assertEquals(
      DateUtils.millisToNanos(WALL_CLOCK_MILLIS),
      span.computeNanoStartTimestampForChild(),
    )
  }

  @Test
  fun `keeps nanosecond resolution even though the wall anchor is millisecond-quantized`() {
    val clock = FakeClock()
    val span = anchoredSpan(WALL_CLOCK_MILLIS, clock)
    val wallClockNanos = DateUtils.millisToNanos(WALL_CLOCK_MILLIS)

    clock.advanceNanos(200_000L)
    val earlier = span.computeNanoStartTimestampForChild()!!
    clock.advanceNanos(600_000L)
    val later = span.computeNanoStartTimestampForChild()!!

    // Both fall inside the same wall-clock millisecond, yet stay distinct and ordered — the
    // resolution comes off the monotonic clock, not off the anchor.
    assertTrue(earlier > wallClockNanos)
    assertTrue(later - earlier == 600_000L)
    assertTrue(later - wallClockNanos < MILLISECONDS.toNanos(1))
  }

  @Test
  fun `returns null when the parent span is not anchored`() {
    val span = mock<ISpan>()
    whenever(span.anchor()).thenReturn(null)

    assertNull(span.computeNanoStartTimestampForChild())
  }

  private fun anchoredSpan(wallClockMillis: Long, clock: FakeClock): ISpan {
    val epoch = EpochClock { Timestamp.ofEpochNanos(DateUtils.millisToNanos(wallClockMillis)) }
    val span = mock<ISpan>()
    whenever(span.anchor()).thenReturn(AnchoredClock.create(epoch, clock))
    return span
  }

  private class FakeClock : MonotonicClock {
    private var nanos = 0L

    override fun tickNanos(): Long = nanos

    fun advanceNanos(amount: Long) {
      nanos += amount
    }
  }

  companion object {
    private const val WALL_CLOCK_MILLIS = 1_000_000L
  }
}
