package io.sentry

import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import org.mockito.kotlin.mock

class NoOpSpanTest {
  private val span = NoOpSpan.getInstance()

  @Test
  fun `startChild does not return null`() {
    assertNotNull(span.startChild("op"))
    assertNotNull(span.startChild("op", "desc"))
  }

  @Test
  fun `startChild with timestamp returns no-op span`() {
    assertThat(span.startChild("op", "desc", SentryLongDate(1234))).isSameInstanceAs(span)
  }

  @Test
  fun `getSpanContext does not return null`() {
    assertNotNull(span.spanContext)
  }

  @Test
  fun `getOperation does not return null`() {
    assertNotNull(span.operation)
  }

  @Test
  fun `updateEndDate return false`() {
    assertFalse(span.updateEndDate(mock()))
  }

  @Test
  fun `startDate return a NanotimeDate`() {
    assertIs<SentryNanotimeDate>(span.startDate)
  }

  @Test
  fun `finishDate return a NanotimeDate`() {
    assertIs<SentryNanotimeDate>(span.finishDate)
  }
}
