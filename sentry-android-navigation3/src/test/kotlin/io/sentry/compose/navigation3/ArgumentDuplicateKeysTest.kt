package io.sentry.compose.navigation3

import com.google.common.truth.Truth.assertThat
import io.sentry.ILogger
import io.sentry.SentryLevel.WARNING
import io.sentry.compose.navigation3.internal.backstack.ArgumentDropReason
import io.sentry.compose.navigation3.internal.backstack.BackStackConverter
import org.junit.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class ArgumentDuplicateKeysTest {

  private val logger = mock<ILogger>()

  @Test
  fun `stringified key collisions drop only that entry`() {
    val sanitizer = BackStackConverter.ArgumentSanitizer(logger, BackStackConverter.WarningState())

    val result =
      sanitizer.sanitizeEntry(
        mapOf("nested" to linkedMapOf<Any, Any>(1 to "first", "1" to "second")),
      )
    assertThat(result.values).isEmpty()
    assertThat(result.dropReason).isEqualTo(ArgumentDropReason.DUPLICATE_KEY)
    verify(logger)
      .log(
        eq(WARNING),
        eq(
          "Nav3 map keys stringify to the same string. Dropping this entry's arguments.",
        ),
      )

    val next = sanitizer.sanitizeEntry(mapOf("id" to 123))
    assertThat(next.dropReason).isNull()
    assertThat(next.values).containsExactly("id", 123)
  }
}
