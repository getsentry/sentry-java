package io.sentry.time

import java.util.concurrent.TimeUnit

/**
 * A [Ticker] that only moves when a test tells it to.
 *
 * Advancing by an amount *and a unit* is the point: a stubbed `thenReturn(1001)` against a
 * nanosecond clock is off by a factor of a million and still compiles, whereas `advance(1001,
 * MILLISECONDS)` cannot be.
 *
 * Implements both clock guarantees so a test can inject it wherever either is declared. Production
 * code must never do this — the whole purpose of the two interfaces is that one object cannot
 * honestly promise both.
 */
class TestTicker(private var nanos: Long = 0) : UptimeClock, ElapsedRealtimeClock {
  override fun tickNanos(): Long = nanos

  fun advance(amount: Long, unit: TimeUnit) {
    nanos += unit.toNanos(amount)
  }
}
