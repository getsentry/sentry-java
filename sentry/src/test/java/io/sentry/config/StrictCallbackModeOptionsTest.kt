package io.sentry.config

import com.google.common.truth.Truth.assertThat
import io.sentry.ExternalOptions
import io.sentry.NoOpLogger
import io.sentry.SentryOptions
import java.util.Properties
import kotlin.test.Test

class StrictCallbackModeOptionsTest {
  @Test
  fun `strict callback mode is opt in`() {
    val options = SentryOptions()
    assertThat(options.isStrictCallbackMode).isFalse()
    options.isStrictCallbackMode = true
    assertThat(options.isStrictCallbackMode).isTrue()
    options.isStrictCallbackMode = false
    assertThat(options.isStrictCallbackMode).isFalse()
  }

  @Test
  fun `external strict callback mode parses true false and absent`() {
    for (value in listOf("true", "false", null)) {
      val properties =
        Properties().apply { if (value != null) setProperty("strict-callback-mode", value) }
      val external =
        ExternalOptions.from(SimplePropertiesProvider(properties), NoOpLogger.getInstance())
      assertThat(external.strictCallbackMode).isEqualTo(value?.toBoolean())
      for (initial in listOf(false, true)) {
        val options = SentryOptions().apply { isStrictCallbackMode = initial }
        options.merge(external)
        assertThat(options.isStrictCallbackMode).isEqualTo(value?.toBoolean() ?: initial)
      }
    }
  }
}
