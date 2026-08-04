package io.sentry.compose.navigation3

import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import kotlin.test.assertFailsWith

class SentryNavOptionsTest {

  @Test
  fun `rejects non-positive max captured backstack entries`() {
    val exception =
      assertFailsWith<IllegalArgumentException> {
        SentryNavOptions(maxCapturedBackStackEntries = 0)
      }

    assertThat(exception)
      .hasMessageThat()
      .isEqualTo("maxCapturedBackStackEntries must be positive, was 0")
  }
}
