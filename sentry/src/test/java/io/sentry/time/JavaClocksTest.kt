package io.sentry.time

import com.google.common.truth.Truth.assertThat
import kotlin.test.Test

class JavaClocksTest {
  @Test
  fun `each clock is a singleton`() {
    assertThat(JavaUptimeClock.getInstance()).isSameInstanceAs(JavaUptimeClock.getInstance())
    assertThat(JavaElapsedRealtimeClock.getInstance())
      .isSameInstanceAs(JavaElapsedRealtimeClock.getInstance())
  }

  @Test
  fun `ticks do not go backwards`() {
    for (clock in listOf(JavaUptimeClock.getInstance(), JavaElapsedRealtimeClock.getInstance())) {
      assertThat(clock.tickNanos()).isAtMost(clock.tickNanos())
    }
  }
}
