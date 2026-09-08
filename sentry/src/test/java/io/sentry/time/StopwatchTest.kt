package io.sentry.time

import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.NANOSECONDS
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.test.Test
import kotlin.test.assertEquals

class StopwatchTest {
  @Test
  fun `starts at zero`() {
    assertEquals(0, Stopwatch.started(TestMonotonicTicker()).elapsedNanos())
  }

  @Test
  fun `reports elapsed time in the requested unit`() {
    val ticker = TestMonotonicTicker()
    val stopwatch = Stopwatch.started(ticker)

    ticker.advance(1500, MILLISECONDS)

    assertEquals(1, stopwatch.elapsed(SECONDS))
    assertEquals(1500, stopwatch.elapsed(MILLISECONDS))
    assertEquals(MILLISECONDS.toNanos(1500), stopwatch.elapsed(NANOSECONDS))
  }

  @Test
  fun `keeps running across reads`() {
    val ticker = TestMonotonicTicker()
    val stopwatch = Stopwatch.started(ticker)

    ticker.advance(1, SECONDS)
    assertEquals(1, stopwatch.elapsed(SECONDS))

    ticker.advance(2, SECONDS)
    assertEquals(3, stopwatch.elapsed(SECONDS))
  }
}
