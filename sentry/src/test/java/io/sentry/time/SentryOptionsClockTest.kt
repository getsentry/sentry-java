package io.sentry.time

import com.google.common.truth.Truth.assertThat
import io.sentry.SentryOptions
import kotlin.test.Test

class SentryOptionsClockTest {
  @Test
  fun `defaults to the JVM clocks`() {
    val options = SentryOptions()

    assertThat(options.uptimeClock).isInstanceOf(JavaUptimeClock::class.java)
    assertThat(options.elapsedRealtimeClock).isInstanceOf(JavaElapsedRealtimeClock::class.java)
  }

  @Test
  fun `the default clocks are singletons`() {
    assertThat(SentryOptions().uptimeClock).isSameInstanceAs(JavaUptimeClock.getInstance())
    assertThat(SentryOptions().elapsedRealtimeClock)
      .isSameInstanceAs(JavaElapsedRealtimeClock.getInstance())
  }

  @Test
  fun `a platform can replace either clock`() {
    val options = SentryOptions()
    val ticker = TestTicker()

    options.uptimeClock = ticker
    options.elapsedRealtimeClock = ticker

    assertThat(options.uptimeClock).isSameInstanceAs(ticker)
    assertThat(options.elapsedRealtimeClock).isSameInstanceAs(ticker)
  }
}
