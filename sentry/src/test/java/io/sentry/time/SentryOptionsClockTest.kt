package io.sentry.time

import com.google.common.truth.Truth.assertThat
import io.sentry.SentryOptions
import kotlin.test.Test

class SentryOptionsClockTest {
  @Test
  fun `defaults to the JVM clock`() {
    assertThat(SentryOptions().elapsedRealtimeClock)
      .isInstanceOf(JavaElapsedRealtimeClock::class.java)
  }

  @Test
  fun `the default clock is a singleton`() {
    assertThat(SentryOptions().elapsedRealtimeClock)
      .isSameInstanceAs(JavaElapsedRealtimeClock.getInstance())
  }

  @Test
  fun `a platform can replace the clock`() {
    val options = SentryOptions()
    val ticker = TestTicker()

    options.elapsedRealtimeClock = ticker

    assertThat(options.elapsedRealtimeClock).isSameInstanceAs(ticker)
  }
}
