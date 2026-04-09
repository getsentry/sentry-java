package io.sentry.spring.jakarta.kafka

import io.sentry.IScopes
import io.sentry.SentryOptions
import io.sentry.SentryTraceHeader
import io.sentry.SentryTracer
import io.sentry.TransactionContext
import java.nio.charset.StandardCharsets
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SentryProducerInterceptorTest {

  private lateinit var scopes: IScopes
  private lateinit var options: SentryOptions

  @BeforeTest
  fun setup() {
    scopes = mock()
    options =
      SentryOptions().apply {
        dsn = "https://key@sentry.io/proj"
        isEnableQueueTracing = true
      }
    whenever(scopes.options).thenReturn(options)
  }

  private fun createTransaction(): SentryTracer {
    val tx = SentryTracer(TransactionContext("tx", "op"), scopes)
    whenever(scopes.span).thenReturn(tx)
    return tx
  }

  @Test
  fun `creates queue publish span with correct op and data`() {
    val tx = createTransaction()
    val interceptor = SentryProducerInterceptor<String, String>(scopes)
    val record = ProducerRecord<String, String>("my-topic", "key", "value")

    interceptor.onSend(record)

    assertEquals(1, tx.spans.size)
    val span = tx.spans.first()
    assertEquals("queue.publish", span.operation)
    assertEquals("my-topic", span.description)
    assertEquals("kafka", span.data["messaging.system"])
    assertEquals("my-topic", span.data["messaging.destination.name"])
    assertTrue(span.isFinished)
  }

  @Test
  fun `does not create span when queue tracing is disabled`() {
    val tx = createTransaction()
    options.isEnableQueueTracing = false
    val interceptor = SentryProducerInterceptor<String, String>(scopes)
    val record = ProducerRecord<String, String>("my-topic", "key", "value")

    interceptor.onSend(record)

    assertEquals(0, tx.spans.size)
  }

  @Test
  fun `does not create span when no active span`() {
    whenever(scopes.span).thenReturn(null)
    val interceptor = SentryProducerInterceptor<String, String>(scopes)
    val record = ProducerRecord<String, String>("my-topic", "key", "value")

    val result = interceptor.onSend(record)

    assertSame(record, result)
  }

  @Test
  fun `injects sentry-trace, baggage, and enqueued-time headers`() {
    createTransaction()
    val interceptor = SentryProducerInterceptor<String, String>(scopes)
    val record = ProducerRecord<String, String>("my-topic", "key", "value")

    val result = interceptor.onSend(record)

    val resultHeaders = result.headers()
    val sentryTraceHeader = resultHeaders.lastHeader(SentryTraceHeader.SENTRY_TRACE_HEADER)
    assertNotNull(sentryTraceHeader, "sentry-trace header should be injected")

    val enqueuedTimeHeader =
      resultHeaders.lastHeader(SentryProducerInterceptor.SENTRY_ENQUEUED_TIME_HEADER)
    assertNotNull(enqueuedTimeHeader, "sentry-task-enqueued-time header should be injected")
    val enqueuedTime = String(enqueuedTimeHeader.value(), StandardCharsets.UTF_8).toLong()
    assertTrue(enqueuedTime > 0, "enqueued time should be a positive epoch millis value")
  }

  @Test
  fun `span is finished synchronously in onSend`() {
    val tx = createTransaction()
    val interceptor = SentryProducerInterceptor<String, String>(scopes)
    val record = ProducerRecord<String, String>("my-topic", "key", "value")

    interceptor.onSend(record)

    assertEquals(1, tx.spans.size)
    assertTrue(tx.spans.first().isFinished, "span should be finished after onSend returns")
  }

  @Test
  fun `onAcknowledgement does not throw`() {
    val interceptor = SentryProducerInterceptor<String, String>(scopes)
    val metadata = RecordMetadata(TopicPartition("my-topic", 0), 0, 0, 0, 0, 0)

    interceptor.onAcknowledgement(metadata, null)
  }

  @Test
  fun `close does not throw`() {
    val interceptor = SentryProducerInterceptor<String, String>(scopes)

    interceptor.close()
  }

  @Test
  fun `trace origin is set correctly`() {
    assertEquals("auto.queue.spring_jakarta.kafka.producer", SentryProducerInterceptor.TRACE_ORIGIN)
  }
}
