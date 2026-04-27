package io.sentry.kafka

import io.sentry.BaggageHeader
import io.sentry.IScopes
import io.sentry.ISentryLifecycleToken
import io.sentry.ISpan
import io.sentry.Scope
import io.sentry.ScopeCallback
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.SentryTraceHeader
import io.sentry.SentryTracer
import io.sentry.SpanOptions
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import io.sentry.test.initForTest
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.apache.kafka.clients.producer.Callback
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.header.Header
import org.apache.kafka.common.header.Headers
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SentryKafkaProducerTest {

  private lateinit var scopes: IScopes
  private lateinit var options: SentryOptions
  private lateinit var delegate: Producer<String, String>

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
    doAnswer { (it.arguments[0] as ScopeCallback).run(Scope(options)) }
      .whenever(scopes)
      .configureScope(any())
    delegate = mock()
    whenever(delegate.send(any(), any())).thenReturn(CompletableFuture.completedFuture(null))
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
    val producer = SentryKafkaProducer(delegate, scopes)
    val record = ProducerRecord<String, String>("my-topic", "key", "value")

    producer.send(record)

    assertEquals(1, tx.spans.size)
    val span = tx.spans.first()
    assertEquals("queue.publish", span.operation)
    assertEquals("my-topic", span.description)
    assertEquals("kafka", span.data["messaging.system"])
    assertEquals("my-topic", span.data["messaging.destination.name"])
    assertEquals(SentryKafkaProducer.TRACE_ORIGIN, span.spanContext.origin)

    val sentryTraceHeader = record.headers().lastHeader(SentryTraceHeader.SENTRY_TRACE_HEADER)
    assertNotNull(sentryTraceHeader)

    val enqueuedTimeHeader =
      record.headers().lastHeader(SentryKafkaProducer.SENTRY_ENQUEUED_TIME_HEADER)
    assertNotNull(enqueuedTimeHeader)
    val enqueuedTimeRaw = String(enqueuedTimeHeader.value(), StandardCharsets.UTF_8)
    // Cross-SDK consumers (e.g. sentry-python) parse this as a plain decimal — must not use
    // scientific notation.
    assertFalse(enqueuedTimeRaw.contains('E') || enqueuedTimeRaw.contains('e'))
    assertTrue(enqueuedTimeRaw.matches(Regex("""^\d+\.\d{6}$""")))
  }

  @Test
  fun `delegates send and does not finish span synchronously`() {
    val tx = createTransaction()
    val producer = SentryKafkaProducer(delegate, scopes)
    val record = ProducerRecord<String, String>("my-topic", "key", "value")

    producer.send(record)

    verify(delegate).send(eq(record), any<Callback>())
    val span = tx.spans.first()
    assertFalse(span.isFinished, "span should be open until callback fires")
  }

  @Test
  fun `finishes span as OK when broker ack callback succeeds`() {
    val tx = createTransaction()
    val producer = SentryKafkaProducer(delegate, scopes)
    val record = ProducerRecord<String, String>("my-topic", "key", "value")

    producer.send(record)

    val captor = argumentCaptor<Callback>()
    verify(delegate).send(eq(record), captor.capture())
    val metadata = RecordMetadata(TopicPartition("my-topic", 0), 0L, 0, 0L, 0, 0)
    captor.firstValue.onCompletion(metadata, null)

    val span = tx.spans.first()
    assertTrue(span.isFinished)
    assertEquals(SpanStatus.OK, span.status)
  }

  @Test
  fun `finishes span as INTERNAL_ERROR when broker ack callback fails`() {
    val tx = createTransaction()
    val producer = SentryKafkaProducer(delegate, scopes)
    val record = ProducerRecord<String, String>("my-topic", "key", "value")
    val exception = RuntimeException("boom")

    producer.send(record)

    val captor = argumentCaptor<Callback>()
    verify(delegate).send(eq(record), captor.capture())
    captor.firstValue.onCompletion(null, exception)

    val span = tx.spans.first()
    assertTrue(span.isFinished)
    assertEquals(SpanStatus.INTERNAL_ERROR, span.status)
    assertSame(exception, span.throwable)
  }

  @Test
  fun `forwards user callback after finishing span`() {
    createTransaction()
    val producer = SentryKafkaProducer(delegate, scopes)
    val record = ProducerRecord<String, String>("my-topic", "key", "value")
    val userCallback = mock<Callback>()

    producer.send(record, userCallback)

    val captor = argumentCaptor<Callback>()
    verify(delegate).send(eq(record), captor.capture())
    val metadata = RecordMetadata(TopicPartition("my-topic", 0), 0L, 0, 0L, 0, 0)
    captor.firstValue.onCompletion(metadata, null)

    verify(userCallback).onCompletion(metadata, null)
  }

  @Test
  fun `finishes span with error when delegate send throws synchronously`() {
    val tx = createTransaction()
    val exception = RuntimeException("kaboom")
    whenever(delegate.send(any(), any())).thenThrow(exception)
    val producer = SentryKafkaProducer(delegate, scopes)
    val record = ProducerRecord<String, String>("my-topic", "key", "value")

    val thrown = runCatching { producer.send(record) }.exceptionOrNull()

    assertSame(exception, thrown)
    val span = tx.spans.first()
    assertTrue(span.isFinished)
    assertEquals(SpanStatus.INTERNAL_ERROR, span.status)
    assertSame(exception, span.throwable)
  }

  @Test
  fun `delegates send without span when queue tracing is disabled`() {
    createTransaction()
    options.isEnableQueueTracing = false
    val producer = SentryKafkaProducer(delegate, scopes)
    val record = ProducerRecord<String, String>("my-topic", "key", "value")

    producer.send(record)

    verify(delegate).send(eq(record), isNull())
  }

  @Test
  fun `delegates send without span when trace origin is ignored`() {
    val tx = createTransaction()
    options.setIgnoredSpanOrigins(listOf(SentryKafkaProducer.TRACE_ORIGIN))
    val producer = SentryKafkaProducer(delegate, scopes)
    val record = ProducerRecord<String, String>("my-topic", "key", "value")

    producer.send(record)

    assertEquals(0, tx.spans.size)
    verify(delegate).send(eq(record), isNull())
    assertEquals(null, record.headers().lastHeader(SentryTraceHeader.SENTRY_TRACE_HEADER))
  }

  @Test
  fun `injects headers but creates no span when no active span`() {
    whenever(scopes.span).thenReturn(null)
    val producer = SentryKafkaProducer(delegate, scopes)
    val record = ProducerRecord<String, String>("my-topic", "key", "value")

    producer.send(record)

    verify(delegate).send(eq(record), isNull())
    // Headers should still be injected from PropagationContext
    assertNotNull(record.headers().lastHeader(SentryTraceHeader.SENTRY_TRACE_HEADER))
    assertNotNull(record.headers().lastHeader(BaggageHeader.BAGGAGE_HEADER))
    assertNotNull(record.headers().lastHeader(SentryKafkaProducer.SENTRY_ENQUEUED_TIME_HEADER))
  }

  @Test
  fun `preserves pre-existing third-party baggage header entries`() {
    createTransaction()
    val producer = SentryKafkaProducer(delegate, scopes)
    val record = ProducerRecord<String, String>("my-topic", "key", "value")
    record
      .headers()
      .add(
        BaggageHeader.BAGGAGE_HEADER,
        "othervendor=someValue,another=thing".toByteArray(StandardCharsets.UTF_8),
      )

    producer.send(record)

    val baggageHeaders = record.headers().headers(BaggageHeader.BAGGAGE_HEADER).toList()
    assertEquals(1, baggageHeaders.size)
    val baggageValue = String(baggageHeaders.first().value(), StandardCharsets.UTF_8)
    assertTrue(baggageValue.contains("othervendor=someValue"))
    assertTrue(baggageValue.contains("another=thing"))
    assertTrue(baggageValue.contains("sentry-"))
  }

  @Test
  fun `header injection failure does not prevent send`() {
    val activeSpan = mock<ISpan>()
    val span = mock<ISpan>()
    val headers = mock<Headers>()
    val record = mock<ProducerRecord<String, String>>()
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
    whenever(headers.remove(SentryTraceHeader.SENTRY_TRACE_HEADER))
      .thenThrow(RuntimeException("boom"))

    val producer = SentryKafkaProducer(delegate, scopes)
    producer.send(record)

    // Header injection failed silently; send still proceeds with wrapped callback for span
    // lifecycle.
    verify(delegate).send(eq(record), any<Callback>())
  }

  @Test
  fun `delegates non-send methods to underlying producer`() {
    val producer = SentryKafkaProducer(delegate, scopes)

    producer.flush()
    producer.partitionsFor("my-topic")
    producer.metrics()
    producer.close()

    verify(delegate).flush()
    verify(delegate).partitionsFor("my-topic")
    verify(delegate).metrics()
    verify(delegate).close()
  }

  @Test
  fun `no-arg constructor uses current scopes`() {
    val transaction = Sentry.startTransaction("tx", "op")
    val record = ProducerRecord<String, String>("my-topic", "key", "value")

    try {
      val token: ISentryLifecycleToken = transaction.makeCurrent()
      try {
        val producer = SentryKafkaProducer(delegate)
        producer.send(record)
      } finally {
        token.close()
      }
    } finally {
      transaction.finish()
    }

    assertNotNull(record.headers().lastHeader(SentryTraceHeader.SENTRY_TRACE_HEADER))
    assertNotNull(record.headers().lastHeader(SentryKafkaProducer.SENTRY_ENQUEUED_TIME_HEADER))
    verify(delegate).send(eq(record), any<Callback>())
  }

  @Test
  fun `getDelegate exposes wrapped producer`() {
    val producer = SentryKafkaProducer(delegate, scopes)
    assertSame(delegate, producer.delegate)
  }

  @Test
  fun `wraps callback even when child span is no-op`() {
    val tx = createTransaction()
    // Set max spans to 1 so the child span is no-op (over limit)
    options.maxSpans = 0
    val producer = SentryKafkaProducer(delegate, scopes)
    val record = ProducerRecord<String, String>("my-topic", "key", "value")

    producer.send(record)

    // Callback is still wrapped (no-op span finish is harmless)
    verify(delegate).send(eq(record), any<Callback>())
    // Headers should still be injected from PropagationContext
    assertNotNull(record.headers().lastHeader(SentryTraceHeader.SENTRY_TRACE_HEADER))
    assertNotNull(record.headers().lastHeader(BaggageHeader.BAGGAGE_HEADER))
    assertNotNull(record.headers().lastHeader(SentryKafkaProducer.SENTRY_ENQUEUED_TIME_HEADER))
  }
}
