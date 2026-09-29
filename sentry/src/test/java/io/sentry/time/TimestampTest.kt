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
  fun `compares by instant, whatever produced it`() {
    val anchored = AnchoredClock.create(FixedEpochClock(42), TestMonotonicTicker())

    assertThat(Timestamp.ofEpochNanos(42)).isEqualTo(anchored.startTime())
    assertThat(Timestamp.ofEpochNanos(42).hashCode()).isEqualTo(anchored.startTime().hashCode())
    assertNotEquals(Timestamp.ofEpochNanos(42), Timestamp.ofEpochNanos(43))
  }
}
