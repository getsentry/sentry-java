package io.sentry.time

import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import kotlin.test.assertNotEquals

class TimestampTest {
  @Test
  fun `keeps the epoch value it was given`() {
    assertThat(Timestamp.ofEpochNanos(1_700_000_000_000_000_000).epochNanos())
      .isEqualTo(1_700_000_000_000_000_000)
  }

  @Test
  fun `an instant read directly has no anchor`() {
    assertThat(Timestamp.ofEpochNanos(42).anchor()).isNull()
  }

  @Test
  fun `an instant a clock projected carries that clock`() {
    val anchored = AnchoredClock.create(SystemEpochClock.getInstance(), TestMonotonicClock())

    assertThat(anchored.now().anchor()).isSameInstanceAs(anchored)
  }

  @Test
  fun `compares by instant, whatever produced it`() {
    val anchored = AnchoredClock.create(FixedEpochClock(42), TestMonotonicClock())

    assertThat(Timestamp.ofEpochNanos(42)).isEqualTo(anchored.start())
    assertThat(Timestamp.ofEpochNanos(42).hashCode()).isEqualTo(anchored.start().hashCode())
    assertNotEquals(Timestamp.ofEpochNanos(42), Timestamp.ofEpochNanos(43))
  }
}
