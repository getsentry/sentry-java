package io.sentry.time

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.test.Test

class AnchoredClockTest {
  private val epoch = FixedEpochClock(SECONDS.toNanos(1_700_000_000))
  private val ticker = TestMonotonicTicker(SECONDS.toNanos(5_000))
  private val anchored = AnchoredClock.create(epoch, ticker)

  @Test
  fun `startTime is the wall-clock reading the anchor was taken at`() {
    assertThat(anchored.startTime().epochNanos()).isEqualTo(SECONDS.toNanos(1_700_000_000))
  }

  @Test
  fun `now is the anchor plus the time measured since`() {
    ticker.advance(120, MILLISECONDS)

    assertThat(anchored.now().epochNanos())
      .isEqualTo(SECONDS.toNanos(1_700_000_000) + MILLISECONDS.toNanos(120))
  }

  @Test
  fun `a wall-clock step does not move a projected instant`() {
    ticker.advance(120, MILLISECONDS)
    epoch.epochNanos -= SECONDS.toNanos(30)

    assertThat(anchored.now().epochNanos())
      .isEqualTo(SECONDS.toNanos(1_700_000_000) + MILLISECONDS.toNanos(120))
  }

  @Test
  fun `two projected instants differ by measured time, across a wall-clock step`() {
    val start = anchored.now()
    epoch.epochNanos += SECONDS.toNanos(30)
    ticker.advance(750, MILLISECONDS)
    val end = anchored.now()

    assertThat(end.epochNanos() - start.epochNanos()).isEqualTo(MILLISECONDS.toNanos(750))
  }

  @Test
  fun `a millisecond anchor still projects nanoseconds`() {
    ticker.advance(1_234, java.util.concurrent.TimeUnit.NANOSECONDS)

    assertThat(anchored.now().epochNanos()).isEqualTo(SECONDS.toNanos(1_700_000_000) + 1_234)
  }
}
