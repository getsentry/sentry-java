package io.sentry.time

import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.MINUTES
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeadlineTest {
  @Test
  fun `has not passed before the deadline`() {
    val clock = TestTicker()
    val deadline = Deadline.after(clock, 2, MINUTES)

    clock.advance(119, SECONDS)

    assertFalse(deadline.hasPassed())
  }

  @Test
  fun `has passed once the deadline is reached`() {
    val clock = TestTicker()
    val deadline = Deadline.after(clock, 2, MINUTES)

    clock.advance(2, MINUTES)

    assertTrue(deadline.hasPassed())
  }

  @Test
  fun `a passed deadline is never fresh, even at tick zero`() {
    // Regression guard: elapsedRealtimeNanos and uptimeMillis both start at 0 on boot, so a
    // numeric sentinel of 0 would read as fresh for a whole TTL after every boot.
    assertTrue(Deadline.passed(TestTicker()).hasPassed())
  }

  @Test
  fun `remaining counts down and floors at zero`() {
    val clock = TestTicker()
    val deadline = Deadline.after(clock, 1000, MILLISECONDS)

    assertEquals(1000, deadline.remaining(MILLISECONDS))

    clock.advance(400, MILLISECONDS)
    assertEquals(600, deadline.remaining(MILLISECONDS))

    clock.advance(10, MINUTES)
    assertEquals(0, deadline.remaining(MILLISECONDS))
  }

  @Test
  fun `remaining rounds up so callers never wake before the deadline`() {
    val clock = TestTicker()
    val deadline = Deadline.after(clock, 1000, MILLISECONDS)

    // half a millisecond in: 999.5ms left, which must not report as 999
    clock.advance(500, java.util.concurrent.TimeUnit.MICROSECONDS)

    assertEquals(1000, deadline.remaining(MILLISECONDS))
  }

  @Test
  fun `isAfter compares two deadlines`() {
    val clock = TestTicker()
    val shorter = Deadline.after(clock, 1, SECONDS)
    val longer = Deadline.after(clock, 5, SECONDS)

    assertTrue(longer.isAfter(shorter))
    assertFalse(shorter.isAfter(longer))
  }

  @Test
  fun `isAfter rejects deadlines from different clocks`() {
    val deadline = Deadline.after(TestTicker(), 1, SECONDS)
    val fromAnotherClock = Deadline.after(TestTicker(), 5, SECONDS)

    assertFailsWith<IllegalArgumentException> { deadline.isAfter(fromAnotherClock) }
  }

  @Test
  fun `comparisons hold when the tick origin is negative`() {
    // System.nanoTime() may start negative; only differences are meaningful.
    val clock = TestTicker(Long.MIN_VALUE + 1)
    val deadline = Deadline.after(clock, 1, SECONDS)

    assertFalse(deadline.hasPassed())
    clock.advance(1, SECONDS)
    assertTrue(deadline.hasPassed())
  }
}
