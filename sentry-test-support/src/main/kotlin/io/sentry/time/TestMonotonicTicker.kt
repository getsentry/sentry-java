package io.sentry.time

import java.util.concurrent.TimeUnit

/**
 * A [MonotonicTicker] that only moves when a test tells it to.
 *
 * Advancing by an amount *and a unit* is the point: a stubbed `thenReturn(1001)` against a
 * nanosecond ticker is off by a factor of a million and still compiles, whereas `advance(1001,
 * MILLISECONDS)` cannot be.
 */
class TestMonotonicTicker(@Volatile private var nanos: Long = 0) : MonotonicTicker {
  override fun tickNanos(): Long = nanos

  fun advance(amount: Long, unit: TimeUnit) {
    nanos += unit.toNanos(amount)
  }
}
