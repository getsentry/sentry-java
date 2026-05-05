package io.sentry.kafka

import io.sentry.BaggageHeader
import io.sentry.IScopes
import io.sentry.ISentryLifecycleToken
import io.sentry.ISpan
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.SentryTraceHeader
import io.sentry.SentryTracer
import io.sentry.SpanOptions
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import io.sentry.test.initForTest
import java.nio.charset.StandardCharsets
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.Header
import org.apache.kafka.common.header.Headers
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SentryKafkaProducerInterceptorTest {

  private lateinit var scopes: IScopes
  private lateinit var options: SentryOptions

  @BeforeTest
  fun setup() {
    initForTest {
      it.dsn = "https://key@sentry.io/proj"
      it.isEnableQueueTracing = true
      it.tracesSampleRate = 1.0
    }
    scopes = mock()
    options =
      SentryOptions().apply {
        dsn = "https://key@sentry.io/proj"
        isEnableQueueTracing = true
      }
    whenever(scopes.options).thenReturn(options)
  }

  @AfterTest
  fun teardown() {
    Sentry.close()
  }

  private fun createTransaction(): SentryTracer {
    val tx = SentryTracer(TransactionContext("tx", "op"), scopes)
    whenever(scopes.span).thenReturn(tx)
    return tx
  }

  @Test
  fun `creates queue publish span and injects headers`() {
    val tx = createTransaction()
    val interceptor = SentryKafkaProducerInterceptor<String, String>(scopes)
    val record = ProducerRecord<String, String>("my-topic", "key", "value")

    interceptor.onSend(record)

    assertEquals(1, tx.spans.size)
    val span = tx.spans.first()
    assertEquals("queue.publish", span.operation)
    assertEquals("my-topic", span.description)
    assertEquals("kafka", span.data["messaging.system"])
    assertEquals("my-topic", span.data["messaging.destination.name"])
    assertEquals(SentryKafkaProducerInterceptor.TRACE_ORIGIN, span.spanContext.origin)
    assertTrue(span.isFinished)

    val sentryTraceHeader = record.headers().lastHeader(SentryTraceHeader.SENTRY_TRACE_HEADER)
    assertNotNull(sentryTraceHeader)

    val enqueuedTimeHeader =
      record.headers().lastHeader(SentryKafkaProducerInterceptor.SENTRY_ENQUEUED_TIME_HEADER)
    assertNotNull(enqueuedTimeHeader)
    val enqueuedTime = String(enqueuedTimeHeader.value(), StandardCharsets.UTF_8).toDouble()
    assertTrue(enqueuedTime > 0)
  }

  @Test
  fun `preserves pre-existing third-party baggage header entries`() {
    val tx = createTransaction()
    val interceptor = SentryKafkaProducerInterceptor<String, String>(scopes)
    val record = ProducerRecord<String, String>("my-topic", "key", "value")
    record
      .headers()
      .add(
        BaggageHeader.BAGGAGE_HEADER,
        "othervendor=someValue,another=thing".toByteArray(StandardCharsets.UTF_8),
      )

    interceptor.onSend(record)

    val baggageHeaders = record.headers().headers(BaggageHeader.BAGGAGE_HEADER).toList()
    assertEquals(1, baggageHeaders.size)
    val baggageValue = String(baggageHeaders.first().value(), StandardCharsets.UTF_8)
    assertTrue(
      baggageValue.contains("othervendor=someValue"),
      "expected third-party baggage entry preserved, got: $baggageValue",
    )
    assertTrue(
      baggageValue.contains("another=thing"),
      "expected third-party baggage entry preserved, got: $baggageValue",
    )
    assertTrue(
      baggageValue.contains("sentry-"),
      "expected Sentry baggage entries appended, got: $baggageValue",
    )
  }

  @Test
  fun `finishes span with error when header injection fails`() {
    val activeSpan = mock<ISpan>()
    val span = mock<ISpan>()
    val headers = mock<Headers>()
    val record = mock<ProducerRecord<String, String>>()
    val exception = RuntimeException("boom")
    whenever(scopes.span).thenReturn(activeSpan)
    whenever(activeSpan.startChild(eq("queue.publish"), eq("my-topic"), any<SpanOptions>()))
      .thenReturn(span)
    whenever(span.isNoOp).thenReturn(false)
    whenever(span.isFinished).thenReturn(false)
    whenever(span.toSentryTrace())
      .thenReturn(SentryTraceHeader("2722d9f6ec019ade60c776169d9a8904-cedf5b7571cb4972-1"))
    whenever(span.toBaggageHeader(null)).thenReturn(null)
    whenever(record.topic()).thenReturn("my-topic")
    whenever(record.headers()).thenReturn(headers)
    whenever(headers.headers(BaggageHeader.BAGGAGE_HEADER)).thenReturn(emptyList<Header>())
    whenever(headers.remove(SentryTraceHeader.SENTRY_TRACE_HEADER)).thenThrow(exception)

    val interceptor = SentryKafkaProducerInterceptor<String, String>(scopes)

    interceptor.onSend(record)

    verify(span).setStatus(SpanStatus.INTERNAL_ERROR)
    verify(span).setThrowable(exception)
    verify(span).finish()
  }

  @Test
  fun `does not create span when queue tracing is disabled`() {
    val tx = createTransaction()
    options.isEnableQueueTracing = false
    val interceptor = SentryKafkaProducerInterceptor<String, String>(scopes)

    interceptor.onSend(ProducerRecord("my-topic", "key", "value"))

    assertEquals(0, tx.spans.size)
  }

  @Test
  fun `does not create span when trace origin is ignored`() {
    val tx = createTransaction()
    options.setIgnoredSpanOrigins(listOf(SentryKafkaProducerInterceptor.TRACE_ORIGIN))
    val interceptor = SentryKafkaProducerInterceptor<String, String>(scopes)
    val record = ProducerRecord("my-topic", "key", "value")

    interceptor.onSend(record)

    assertEquals(0, tx.spans.size)
    assertEquals(null, record.headers().lastHeader(SentryTraceHeader.SENTRY_TRACE_HEADER))
    assertEquals(
      null,
      record.headers().lastHeader(SentryKafkaProducerInterceptor.SENTRY_ENQUEUED_TIME_HEADER),
    )
  }

  @Test
  fun `returns original record when no active span`() {
    whenever(scopes.span).thenReturn(null)
    val interceptor = SentryKafkaProducerInterceptor<String, String>(scopes)
    val record = ProducerRecord("my-topic", "key", "value")

    val result = interceptor.onSend(record)

    assertSame(record, result)
  }

  @Test
  fun `no-arg constructor uses current scopes`() {
    val transaction = Sentry.startTransaction("tx", "op")
    val record = ProducerRecord("my-topic", "key", "value")

    try {
      val token: ISentryLifecycleToken = transaction.makeCurrent()
      try {
        val interceptor = SentryKafkaProducerInterceptor<String, String>()
        interceptor.onSend(record)
      } finally {
        token.close()
      }
    } finally {
      transaction.finish()
    }

    assertNotNull(record.headers().lastHeader(SentryTraceHeader.SENTRY_TRACE_HEADER))
    assertNotNull(
      record.headers().lastHeader(SentryKafkaProducerInterceptor.SENTRY_ENQUEUED_TIME_HEADER)
    )
  }
}
