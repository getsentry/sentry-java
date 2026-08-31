package io.sentry.time

import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.NANOSECONDS
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.test.Test
import kotlin.test.assertEquals

class StopwatchTest {
  @Test
  fun `starts at zero`() {
    assertEquals(0, Stopwatch.started(TestTicker()).elapsedNanos())
  }

  @Test
  fun `reports elapsed time in the requested unit`() {
    val clock = TestTicker()
    val stopwatch = Stopwatch.started(clock)

    clock.advance(1500, MILLISECONDS)

    assertEquals(1, stopwatch.elapsed(SECONDS))
    assertEquals(1500, stopwatch.elapsed(MILLISECONDS))
    assertEquals(MILLISECONDS.toNanos(1500), stopwatch.elapsed(NANOSECONDS))
  }

  @Test
  fun `keeps running across reads`() {
    val clock = TestTicker()
    val stopwatch = Stopwatch.started(clock)

    clock.advance(1, SECONDS)
    assertEquals(1, stopwatch.elapsed(SECONDS))

    clock.advance(2, SECONDS)
    assertEquals(3, stopwatch.elapsed(SECONDS))
  }
}
