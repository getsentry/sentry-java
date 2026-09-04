package io.sentry.time

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.test.Test
import kotlin.test.assertFailsWith

class AnchoredClockTest {
  private val epoch = FixedEpochClock(SECONDS.toNanos(1_700_000_000))
  private val clock = TestMonotonicClock(SECONDS.toNanos(5_000))
  private val anchored = AnchoredClock.create(epoch, clock)

  @Test
  fun `start is the epoch reading the anchor was taken at`() {
    assertThat(anchored.start().epochNanos()).isEqualTo(SECONDS.toNanos(1_700_000_000))
  }

  @Test
  fun `now is the anchor plus the time measured since`() {
    clock.advance(120, MILLISECONDS)

    assertThat(anchored.now().epochNanos())
      .isEqualTo(SECONDS.toNanos(1_700_000_000) + MILLISECONDS.toNanos(120))
  }

  @Test
  fun `a wall-clock step does not move a projected instant`() {
    clock.advance(120, MILLISECONDS)
    epoch.epochNanos -= SECONDS.toNanos(30)

    assertThat(anchored.now().epochNanos())
      .isEqualTo(SECONDS.toNanos(1_700_000_000) + MILLISECONDS.toNanos(120))
  }

  @Test
  fun `two projected instants differ by measured time, across a wall-clock step`() {
    val start = anchored.now()
    epoch.epochNanos += SECONDS.toNanos(30)
    clock.advance(750, MILLISECONDS)
    val end = anchored.now()

    assertThat(end.epochNanos() - start.epochNanos()).isEqualTo(MILLISECONDS.toNanos(750))
  }

  @Test
  fun `a millisecond anchor still projects nanoseconds`() {
    clock.advance(1_234, java.util.concurrent.TimeUnit.NANOSECONDS)

    assertThat(anchored.now().epochNanos()).isEqualTo(SECONDS.toNanos(1_700_000_000) + 1_234)
  }

  @Test
  fun `at places a tick measured elsewhere on the same timeline`() {
    val tick = clock.tickNanos() + MILLISECONDS.toNanos(8)

    assertThat(anchored.at(tick).epochNanos())
      .isEqualTo(SECONDS.toNanos(1_700_000_000) + MILLISECONDS.toNanos(8))
  }

  @Test
  fun `tickOf recovers the tick a projection came from`() {
    clock.advance(120, MILLISECONDS)
    val now = anchored.now()

    assertThat(anchored.tickOf(now)).isEqualTo(clock.tickNanos())
  }

  @Test
  fun `tickOf rejects an instant read straight from a wall clock`() {
    assertFailsWith<IllegalArgumentException> {
      anchored.tickOf(Timestamp.ofEpochNanos(SECONDS.toNanos(1_700_000_000)))
    }
  }

  @Test
  fun `tickOf rejects an instant from another anchor`() {
    val other = AnchoredClock.create(epoch, clock)

    assertFailsWith<IllegalArgumentException> { anchored.tickOf(other.now()) }
  }

  @Test
  fun `drift is zero while the wall clock keeps pace`() {
    epoch.epochNanos += MILLISECONDS.toNanos(120)
    clock.advance(120, MILLISECONDS)

    assertThat(anchored.driftNanos()).isEqualTo(0)
  }

  @Test
  fun `drift reports how far the wall clock stepped`() {
    clock.advance(1, SECONDS)
    epoch.epochNanos += SECONDS.toNanos(31)

    assertThat(anchored.driftNanos()).isEqualTo(SECONDS.toNanos(30))
  }

  @Test
  fun `drift is negative when the wall clock steps backwards`() {
    clock.advance(1, SECONDS)
    epoch.epochNanos -= SECONDS.toNanos(4)

    assertThat(anchored.driftNanos()).isEqualTo(SECONDS.toNanos(-5))
  }
}
