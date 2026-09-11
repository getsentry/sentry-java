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
    val ticker = TestMonotonicTicker()
    val deadline = Deadline.after(ticker, 2, MINUTES)

    ticker.advance(119, SECONDS)

    assertFalse(deadline.hasPassed())
  }

  @Test
  fun `has passed once the deadline is reached`() {
    val ticker = TestMonotonicTicker()
    val deadline = Deadline.after(ticker, 2, MINUTES)

    ticker.advance(2, MINUTES)

    assertTrue(deadline.hasPassed())
  }

  @Test
  fun `a passed deadline has passed even when the ticker is at zero`() {
    // The pattern this replaces stored the last-updated tick and compared `now - lastUpdated`
    // against a TTL, with 0 standing in for "never updated". Tick 0 is a real instant though —
    // the moment the device booted — so for the first TTL of every boot, never-updated state
    // read as freshly updated. A passed deadline has no such value to misread.
    assertTrue(Deadline.passed(TestMonotonicTicker()).hasPassed())
  }

  @Test
  fun `remaining counts down and floors at zero`() {
    val ticker = TestMonotonicTicker()
    val deadline = Deadline.after(ticker, 1000, MILLISECONDS)

    assertEquals(1000, deadline.remaining(MILLISECONDS))

    ticker.advance(400, MILLISECONDS)
    assertEquals(600, deadline.remaining(MILLISECONDS))

    ticker.advance(10, MINUTES)
    assertEquals(0, deadline.remaining(MILLISECONDS))
  }

  @Test
  fun `remaining rounds up so callers never wake before the deadline`() {
    val ticker = TestMonotonicTicker()
    val deadline = Deadline.after(ticker, 1000, MILLISECONDS)

    // half a millisecond in: 999.5ms left, which must not report as 999
    ticker.advance(500, java.util.concurrent.TimeUnit.MICROSECONDS)

    assertEquals(1000, deadline.remaining(MILLISECONDS))
  }

  @Test
  fun `after treats a negative amount as already passed`() {
    val ticker = TestMonotonicTicker()

    val deadline = Deadline.after(ticker, -1, SECONDS)

    assertTrue(deadline.hasPassed())
    assertEquals(0, deadline.remaining(MILLISECONDS))
  }

  @Test
  fun `a negative amount does not outlast a standing deadline`() {
    // A bogus Retry-After must not be able to shorten a rate limit that is already in force.
    val ticker = TestMonotonicTicker()
    val standing = Deadline.after(ticker, 60, SECONDS)

    assertFalse(Deadline.after(ticker, -1, SECONDS).isAfter(standing))
  }

  @Test
  fun `isAfter compares two deadlines`() {
    val ticker = TestMonotonicTicker()
    val shorter = Deadline.after(ticker, 1, SECONDS)
    val alsoShorter = Deadline.after(ticker, 1, SECONDS)
    val longer = Deadline.after(ticker, 5, SECONDS)

    assertTrue(longer.isAfter(shorter))
    assertFalse(shorter.isAfter(longer))
    assertFalse(shorter.isAfter(alsoShorter))
  }

  @Test
  fun `isAfter rejects deadlines from different tickers`() {
    val deadline = Deadline.after(TestMonotonicTicker(), 1, SECONDS)
    val fromAnotherTicker = Deadline.after(TestMonotonicTicker(), 1, SECONDS)

    assertFailsWith<IllegalArgumentException> { deadline.isAfter(fromAnotherTicker) }
  }

  @Test
  fun `comparisons hold when the tick origin is negative`() {
    // System.nanoTime() may start negative; only differences are meaningful.
    val ticker = TestMonotonicTicker(Long.MIN_VALUE + 1)
    val deadline = Deadline.after(ticker, 1, SECONDS)

    assertFalse(deadline.hasPassed())
    ticker.advance(1, SECONDS)
    assertTrue(deadline.hasPassed())
  }
}
