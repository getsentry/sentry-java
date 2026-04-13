package io.sentry.kafka

import io.sentry.IScopes
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.SentryTraceHeader
import io.sentry.SentryTracer
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SentryKafkaProducerInterceptorTest {

  private lateinit var scopes: IScopes
  private lateinit var options: SentryOptions

  @BeforeTest
  fun setup() {
    initForTest { it.dsn = "https://key@sentry.io/proj" }
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
  fun `does not create span when queue tracing is disabled`() {
    val tx = createTransaction()
    options.isEnableQueueTracing = false
    val interceptor = SentryKafkaProducerInterceptor<String, String>(scopes)

    interceptor.onSend(ProducerRecord("my-topic", "key", "value"))

    assertEquals(0, tx.spans.size)
  }

  @Test
  fun `returns original record when no active span`() {
    whenever(scopes.span).thenReturn(null)
    val interceptor = SentryKafkaProducerInterceptor<String, String>(scopes)
    val record = ProducerRecord("my-topic", "key", "value")

    val result = interceptor.onSend(record)

    assertSame(record, result)
  }
}
