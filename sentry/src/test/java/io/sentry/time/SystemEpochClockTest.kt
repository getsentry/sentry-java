package io.sentry.time

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlin.test.Test

class SystemEpochClockTest {
  @Test
  fun `now reads the system wall clock`() {
    val before = MILLISECONDS.toNanos(System.currentTimeMillis())
    val now = SystemEpochClock.getInstance().now().epochNanos()
    val after = MILLISECONDS.toNanos(System.currentTimeMillis())

    // the bounds are millisecond-truncated, so now() may sit up to a millisecond past `after`
    assertThat(now).isAtLeast(before)
    assertThat(now).isAtMost(after + MILLISECONDS.toNanos(1))
  }

  @Test
  fun `an instant read from the wall clock is not anchored to anything`() {
    assertThat(SystemEpochClock.getInstance().now().anchor()).isNull()
  }
}
