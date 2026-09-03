package io.sentry.time

import java.util.concurrent.TimeUnit

/**
 * A [MonotonicClock] that only moves when a test tells it to.
 *
 * Advancing by an amount *and a unit* is the point: a stubbed `thenReturn(1001)` against a
 * nanosecond clock is off by a factor of a million and still compiles, whereas `advance(1001,
 * MILLISECONDS)` cannot be.
 */
class TestMonotonicClock(private var nanos: Long = 0) : MonotonicClock {
  override fun tickNanos(): Long = nanos

  fun advance(amount: Long, unit: TimeUnit) {
    nanos += unit.toNanos(amount)
  }
}
