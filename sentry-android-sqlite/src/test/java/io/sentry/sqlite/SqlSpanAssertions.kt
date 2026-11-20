package io.sentry.sqlite

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.sentry.Span

/** Asserts that [canary] appears neither in the description nor in any data value of [span]. */
internal fun assertNoCanary(span: Span, canary: String = "canary") {
  assertThat(span.description.orEmpty()).doesNotContain(canary)
  span.data.forEach { (key, value) ->
    assertWithMessage(key).that(value.toString()).doesNotContain(canary)
  }
}
