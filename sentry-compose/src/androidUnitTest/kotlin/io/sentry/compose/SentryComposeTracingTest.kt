package io.sentry.compose

import io.sentry.ISpan
import io.sentry.NoOpTransportFactory
import io.sentry.Sentry
import io.sentry.SentryDate
import io.sentry.SentryDateProvider
import io.sentry.SentryLongDate
import io.sentry.SpanContext
import io.sentry.SpanOptions
import kotlin.test.assertEquals
import org.junit.After
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SentryComposeTracingTest {

  @After
  fun tearDown() {
    Sentry.close()
  }

  @Test
  fun `onRemembered records successful composition span`() {
    val startTimestamp = SentryLongDate(10)
    val endTimestamp = SentryLongDate(20)
    initSentry(endTimestamp)

    val parentSpan = mock<ISpan>()
    val childSpan = mock<ISpan>()
    val childSpanContext = SpanContext("child")
    whenever(childSpan.spanContext).thenReturn(childSpanContext)
    whenever(parentSpan.startChild(eq("ui.compose"), eq("tag"), any<SpanOptions>()))
      .thenReturn(childSpan)

    CompositionSpanRecorder(parentSpan, "tag", startTimestamp).onRemembered()

    val optionsCaptor = argumentCaptor<SpanOptions>()
    verify(parentSpan).startChild(eq("ui.compose"), eq("tag"), optionsCaptor.capture())
    assertEquals(
      startTimestamp.nanoTimestamp(),
      optionsCaptor.firstValue.startTimestamp?.nanoTimestamp(),
    )
    assertEquals("auto.ui.jetpack_compose", childSpanContext.origin)
    verify(childSpan).setTag("composition.result", "success")
    verify(childSpan).finish(null, endTimestamp)
  }

  @Test
  fun `onAbandoned records abandoned composition span`() {
    val startTimestamp = SentryLongDate(30)
    val endTimestamp = SentryLongDate(40)
    initSentry(endTimestamp)

    val parentSpan = mock<ISpan>()
    val childSpan = mock<ISpan>()
    val childSpanContext = SpanContext("child")
    whenever(childSpan.spanContext).thenReturn(childSpanContext)
    whenever(parentSpan.startChild(eq("ui.compose"), eq("tag"), any<SpanOptions>()))
      .thenReturn(childSpan)

    CompositionSpanRecorder(parentSpan, "tag", startTimestamp).onAbandoned()

    val optionsCaptor = argumentCaptor<SpanOptions>()
    verify(parentSpan).startChild(eq("ui.compose"), eq("tag"), optionsCaptor.capture())
    assertEquals(
      startTimestamp.nanoTimestamp(),
      optionsCaptor.firstValue.startTimestamp?.nanoTimestamp(),
    )
    assertEquals("auto.ui.jetpack_compose", childSpanContext.origin)
    verify(childSpan).setTag("composition.result", "abandoned")
    verify(childSpan).finish(null, endTimestamp)
  }

  private fun initSentry(vararg dates: SentryDate) {
    val iterator = dates.iterator()
    val last = dates.last()

    Sentry.init { options ->
      options.dsn = "https://public@example.com/1"
      options.setTransportFactory(NoOpTransportFactory.getInstance())
      options.dateProvider = SentryDateProvider {
        if (iterator.hasNext()) iterator.next() else last
      }
    }
  }
}
