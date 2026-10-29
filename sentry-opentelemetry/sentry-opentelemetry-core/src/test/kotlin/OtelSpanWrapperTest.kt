package io.sentry.opentelemetry

import com.google.common.truth.Truth.assertThat
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.sentry.IScopes
import io.sentry.ISpan
import io.sentry.ISpanFactory
import io.sentry.SentryLongDate
import io.sentry.SentryOptions
import io.sentry.SpanOptions
import kotlin.test.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class OtelSpanWrapperTest {
  @Test
  fun `startChild with timestamp and options applies timestamp to copy`() {
    val otelSpan = mock<ReadWriteSpan>()
    whenever(otelSpan.spanContext)
      .thenReturn(
        SpanContext.create(
          "2722d9f6ec019ade60c776169d9a8904",
          "cedf5b7571cb4972",
          TraceFlags.getSampled(),
          TraceState.getDefault(),
        )
      )
    whenever(otelSpan.name).thenReturn("parent")

    val spanFactory = mock<ISpanFactory>()
    val options = SentryOptions().apply { this.spanFactory = spanFactory }
    val scopes = mock<IScopes>()
    whenever(scopes.options).thenReturn(options)

    val parent = OtelSpanWrapper(otelSpan, scopes, SentryLongDate(0), null, null, null, null)
    val expectedChild = mock<ISpan>()
    whenever(spanFactory.createSpan(eq(scopes), any(), any(), eq(parent))).thenReturn(expectedChild)
    val originalTimestamp = SentryLongDate(1234)
    val timestamp = SentryLongDate(5678)
    val suppliedSpanOptions = SpanOptions().apply { startTimestamp = originalTimestamp }

    val child = parent.startChild("child-op", "description", timestamp, suppliedSpanOptions)
    val childWithoutTimestamp =
      parent.startChild("second-child-op", "second-description", null, suppliedSpanOptions)

    val spanOptions = argumentCaptor<SpanOptions>()
    val spanContext = argumentCaptor<io.sentry.SpanContext>()
    verify(spanFactory, times(2))
      .createSpan(eq(scopes), spanOptions.capture(), spanContext.capture(), eq(parent))
    assertThat(child).isSameInstanceAs(expectedChild)
    assertThat(childWithoutTimestamp).isSameInstanceAs(expectedChild)
    assertThat(spanOptions.allValues[0]).isNotSameInstanceAs(suppliedSpanOptions)
    assertThat(spanOptions.allValues[0].startTimestamp).isSameInstanceAs(timestamp)
    assertThat(spanOptions.allValues[1]).isNotSameInstanceAs(suppliedSpanOptions)
    assertThat(spanOptions.allValues[1].startTimestamp).isNull()
    assertThat(suppliedSpanOptions.startTimestamp).isSameInstanceAs(originalTimestamp)
    assertThat(spanContext.allValues[0].operation).isEqualTo("child-op")
    assertThat(spanContext.allValues[0].description).isEqualTo("description")
    assertThat(spanContext.allValues[1].operation).isEqualTo("second-child-op")
    assertThat(spanContext.allValues[1].description).isEqualTo("second-description")
  }
}
