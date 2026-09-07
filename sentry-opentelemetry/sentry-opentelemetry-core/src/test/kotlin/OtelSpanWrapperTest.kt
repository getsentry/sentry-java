package io.sentry.opentelemetry

import com.google.common.truth.Truth.assertThat
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.sentry.IScopes
import io.sentry.ISpan
import io.sentry.ISpanFactory
import io.sentry.Instrumenter
import io.sentry.SentryLongDate
import io.sentry.SentryOptions
import io.sentry.SpanOptions
import kotlin.test.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class OtelSpanWrapperTest {
  @Test
  fun `startChild with timestamp forwards timestamp and Sentry instrumenter`() {
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
    val timestamp = SentryLongDate(1234)

    val child = parent.startChild("child-op", "description", timestamp)

    val spanOptions = argumentCaptor<SpanOptions>()
    val spanContext = argumentCaptor<io.sentry.SpanContext>()
    verify(spanFactory)
      .createSpan(eq(scopes), spanOptions.capture(), spanContext.capture(), eq(parent))
    assertThat(child).isSameInstanceAs(expectedChild)
    assertThat(spanOptions.firstValue.startTimestamp).isSameInstanceAs(timestamp)
    assertThat(spanContext.firstValue.operation).isEqualTo("child-op")
    assertThat(spanContext.firstValue.description).isEqualTo("description")
    assertThat(spanContext.firstValue.instrumenter).isEqualTo(Instrumenter.SENTRY)
  }
}
