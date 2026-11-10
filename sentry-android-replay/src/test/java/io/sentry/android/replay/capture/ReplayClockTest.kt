package io.sentry.android.replay.capture

import com.google.common.truth.Truth.assertThat
import io.sentry.time.AnchoredClock
import io.sentry.time.EpochClock
import io.sentry.time.TestMonotonicTicker
import io.sentry.time.Timestamp
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlin.test.Test

class ReplayClockTest {
  private var wallMillis = 1_720_693_523_997L
  private val epochClock = EpochClock { Timestamp.ofEpochNanos(MILLISECONDS.toNanos(wallMillis)) }
  private val ticker = TestMonotonicTicker()

  private fun elapse(millis: Long) {
    wallMillis += millis
    ticker.advance(millis, MILLISECONDS)
  }

  @Test
  fun `follows the wall clock while it does not jump`() {
    val clock = ReplayClock(epochClock, ticker)

    elapse(4_321)

    assertThat(clock.nowMillis()).isEqualTo(wallMillis)
    assertThat(clock.wallClockJumpMillis()).isEqualTo(0)
  }

  @Test
  fun `measures time on the ticker when the wall clock jumps`() {
    val clock = ReplayClock(epochClock, ticker)
    val start = clock.nowMillis()

    elapse(3_000)
    wallMillis -= 120_000

    assertThat(clock.nowMillis()).isEqualTo(start + 3_000)
  }

  @Test
  fun `ignores a jump within the tolerance`() {
    val clock = ReplayClock(epochClock, ticker)

    wallMillis += ReplayClock.JUMP_TOLERANCE_MS
    assertThat(clock.wallClockJumpMillis()).isEqualTo(0)

    wallMillis -= 2 * ReplayClock.JUMP_TOLERANCE_MS
    assertThat(clock.wallClockJumpMillis()).isEqualTo(0)
  }

  @Test
  fun `reports a forward jump`() {
    val clock = ReplayClock(epochClock, ticker)

    wallMillis += 120_000

    assertThat(clock.wallClockJumpMillis()).isEqualTo(120_000)
  }

  @Test
  fun `reports a backward jump`() {
    val clock = ReplayClock(epochClock, ticker)

    wallMillis -= ReplayClock.JUMP_TOLERANCE_MS + 1

    assertThat(clock.wallClockJumpMillis()).isEqualTo(-(ReplayClock.JUMP_TOLERANCE_MS + 1))
  }

  @Test
  fun `restart anchors a new timeline on the wall clock`() {
    val clock = ReplayClock(epochClock, ticker)
    val oldTimeline = clock.timeline
    elapse(3_000)
    wallMillis -= 120_000

    val newStart = clock.restart().startTime().epochMillis

    assertThat(newStart).isEqualTo(wallMillis)
    assertThat(clock.wallClockJumpMillis()).isEqualTo(0)
    elapse(1_000)
    assertThat(clock.nowMillis()).isEqualTo(newStart + 1_000)
    // the old timeline keeps counting on its own, for what was stamped on it
    assertThat(oldTimeline.nowMillis()).isEqualTo(newStart + 120_000 + 1_000)
  }

  @Test
  fun `continues a timeline it is handed`() {
    val timeline = AnchoredClock.create(epochClock, ticker)
    elapse(1_000)
    wallMillis += 5_000

    val clock = ReplayClock(epochClock, ticker, timeline)

    assertThat(clock.nowMillis()).isEqualTo(timeline.nowMillis())
    assertThat(clock.wallClockJumpMillis()).isEqualTo(5_000)
  }
}
