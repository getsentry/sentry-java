package io.sentry.compose.navigation3

import com.google.common.truth.Truth.assertThat
import io.sentry.NoOpLogger
import org.junit.Test

class ArgumentDuplicateKeysTest {
  @Test
  fun `stringified key collisions drop only that entry`() {
    val sanitizer =
      BackStackConverter.ArgumentSanitizer(
        NoOpLogger.getInstance(),
        BackStackConverter.WarningState(),
      )
    val result =
      sanitizer.sanitizeEntry(
        mapOf("nested" to linkedMapOf<Any, Any>(1 to "first", "1" to "second")),
      )
    assertThat(result.values).isEmpty()
    assertThat(result.dropReason).isEqualTo(ArgumentDropReason.DUPLICATE_KEY)
    val next = sanitizer.sanitizeEntry(mapOf("id" to 123))
    assertThat(next.dropReason).isNull()
    assertThat(next.values).containsExactly("id", 123)
  }
}
