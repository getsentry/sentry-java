package io.sentry.internal.eventprocessor

import com.google.common.truth.Truth.assertThat
import io.sentry.EventProcessor
import io.sentry.SentryOptions
import kotlin.test.Test

class SentryEventProcessorTest {
  @Test
  fun `default SDK event processors are marked as Sentry processors`() {
    assertThat(SentryOptions().eventProcessors).isNotEmpty()
    assertThat(SentryOptions().eventProcessors.all { it is SentryEventProcessor }).isTrue()
  }

  @Test
  fun `customer event processors are not marked as Sentry processors`() {
    val processor = object : EventProcessor {}

    assertThat(processor).isNotInstanceOf(SentryEventProcessor::class.java)
  }
}
