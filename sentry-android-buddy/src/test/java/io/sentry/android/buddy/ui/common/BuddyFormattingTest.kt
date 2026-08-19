package io.sentry.android.buddy.ui.common

import com.google.common.truth.Truth.assertThat
import kotlin.test.Test

class BuddyFormattingTest {
  private val now = 1_700_000_000_000L

  private fun ageOf(ageMs: Long): String = relativeTime(now - ageMs, now)

  @Test
  fun `ages below five seconds read as now`() {
    assertThat(ageOf(0)).isEqualTo("now")
    assertThat(ageOf(4_999)).isEqualTo("now")
  }

  @Test
  fun `a timestamp in the future reads as now`() {
    assertThat(relativeTime(now + 10_000, now)).isEqualTo("now")
  }

  @Test
  fun `seconds are rounded down to five-second steps`() {
    assertThat(ageOf(5_000)).isEqualTo("5s")
    assertThat(ageOf(9_999)).isEqualTo("5s")
    assertThat(ageOf(10_000)).isEqualTo("10s")
    assertThat(ageOf(55_000)).isEqualTo("55s")
    assertThat(ageOf(59_999)).isEqualTo("55s")
  }

  @Test
  fun `minutes are rounded down to whole minutes`() {
    assertThat(ageOf(60_000)).isEqualTo("1 min")
    assertThat(ageOf(119_999)).isEqualTo("1 min")
    assertThat(ageOf(120_000)).isEqualTo("2 min")
    assertThat(ageOf(3_600_000)).isEqualTo("60 min")
  }
}
