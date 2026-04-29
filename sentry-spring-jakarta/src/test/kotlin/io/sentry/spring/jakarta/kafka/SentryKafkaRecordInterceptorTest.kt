package io.sentry.spring.jakarta.kafka

import io.sentry.BaggageHeader
import io.sentry.IScopes
import io.sentry.ISentryLifecycleToken
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
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.RecordHeaders
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.kafka.listener.RecordInterceptor

class SentryKafkaRecordInterceptorTest {

  private lateinit var scopes: IScopes
  private lateinit var options: SentryOptions
  private lateinit var consumer: Consumer<String, String>
  private lateinit var lifecycleToken: ISentryLifecycleToken

  @BeforeTest
  fun setup() {
    initForTest { it.dsn = "https://key@sentry.io/proj" }
    scopes = mock()
    consumer = mock()
    lifecycleToken = mock()
    options =
      SentryOptions().apply {
        dsn = "https://key@sentry.io/proj"
        isEnableQueueTracing = true
        tracesSampleRate = 1.0
      }
    whenever(scopes.options).thenReturn(options)
    whenever(scopes.isEnabled).thenReturn(true)

    val forkedScopes = mock<IScopes>()
    whenever(forkedScopes.options).thenReturn(options)
    whenever(forkedScopes.makeCurrent()).thenReturn(lifecycleToken)
    whenever(scopes.forkedScopes(any())).thenReturn(forkedScopes)

    val tx = SentryTracer(TransactionContext("queue.process", "queue.process"), forkedScopes)
    whenever(forkedScopes.startTransaction(any<TransactionContext>(), any())).thenReturn(tx)
  }

  @AfterTest
  fun teardown() {
    Sentry.close()
  }

  private fun createRecord(
    topic: String = "my-topic",
    headers: RecordHeaders = RecordHeaders(),
  ): ConsumerRecord<String, String> {
    val record = ConsumerRecord<String, String>(topic, 0, 0L, "key", "value")
    headers.forEach { record.headers().add(it) }
    return record
  }

  private fun createRecordWithHeaders(
    sentryTrace: String? = null,
    baggage: String? = null,
    enqueuedTime: Long? = null,
  ): ConsumerRecord<String, String> {
    val headers = RecordHeaders()
    sentryTrace?.let {
      headers.add(SentryTraceHeader.SENTRY_TRACE_HEADER, it.toByteArray(StandardCharsets.UTF_8))
    }
    baggage?.let {
      headers.add(BaggageHeader.BAGGAGE_HEADER, it.toByteArray(StandardCharsets.UTF_8))
    }
    enqueuedTime?.let {
      headers.add(
        SentryProducerInterceptor.SENTRY_ENQUEUED_TIME_HEADER,
        it.toString().toByteArray(StandardCharsets.UTF_8),
      )
    }
    val record = ConsumerRecord<String, String>("my-topic", 0, 0L, "key", "value")
    headers.forEach { record.headers().add(it) }
    return record
  }

  @Test
  fun `intercept creates forked scopes`() {
    val interceptor = SentryKafkaRecordInterceptor<String, String>(scopes)
    val record = createRecord()

    interceptor.intercept(record, consumer)

    verify(scopes).forkedScopes("SentryKafkaRecordInterceptor")
  }

  @Test
  fun `intercept continues trace from headers`() {
    val forkedScopes = mock<IScopes>()
    whenever(forkedScopes.options).thenReturn(options)
    whenever(forkedScopes.makeCurrent()).thenReturn(lifecycleToken)
    whenever(scopes.forkedScopes(any())).thenReturn(forkedScopes)

    val tx = SentryTracer(TransactionContext("queue.process", "queue.process"), forkedScopes)
    whenever(forkedScopes.startTransaction(any<TransactionContext>(), any())).thenReturn(tx)

    val interceptor = SentryKafkaRecordInterceptor<String, String>(scopes)
    val sentryTraceValue = "2722d9f6ec019ade60c776169d9a8904-cedf5b7571cb4972-1"
    val record = createRecordWithHeaders(sentryTrace = sentryTraceValue)

    interceptor.intercept(record, consumer)

    verify(forkedScopes)
      .continueTrace(org.mockito.kotlin.eq(sentryTraceValue), org.mockito.kotlin.isNull())
  }

  @Test
  fun `intercept calls continueTrace with null when no headers`() {
    val forkedScopes = mock<IScopes>()
    whenever(forkedScopes.options).thenReturn(options)
    whenever(forkedScopes.makeCurrent()).thenReturn(lifecycleToken)
    whenever(scopes.forkedScopes(any())).thenReturn(forkedScopes)

    val tx = SentryTracer(TransactionContext("queue.process", "queue.process"), forkedScopes)
    whenever(forkedScopes.startTransaction(any<TransactionContext>(), any())).thenReturn(tx)

    val interceptor = SentryKafkaRecordInterceptor<String, String>(scopes)
    val record = createRecord()

    interceptor.intercept(record, consumer)

    verify(forkedScopes).continueTrace(org.mockito.kotlin.isNull(), org.mockito.kotlin.isNull())
  }

  @Test
  fun `does not create span when queue tracing is disabled`() {
    options.isEnableQueueTracing = false
    val interceptor = SentryKafkaRecordInterceptor<String, String>(scopes)
    val record = createRecord()

    val result = interceptor.intercept(record, consumer)

    verify(scopes, never()).forkedScopes(any())
    assertEquals(record, result)
  }

  @Test
  fun `delegates to existing interceptor`() {
    val delegate = mock<RecordInterceptor<String, String>>()
    val record = createRecord()
    whenever(delegate.intercept(record, consumer)).thenReturn(record)

    val interceptor = SentryKafkaRecordInterceptor(scopes, delegate)
    interceptor.intercept(record, consumer)

    verify(delegate).intercept(record, consumer)
  }

  @Test
  fun `success finishes transaction and delegates`() {
    val delegate = mock<RecordInterceptor<String, String>>()
    val interceptor = SentryKafkaRecordInterceptor(scopes, delegate)
    val record = createRecord()

    // intercept first to set up context
    interceptor.intercept(record, consumer)
    interceptor.success(record, consumer)

    verify(delegate).success(record, consumer)
  }

  @Test
  fun `failure finishes transaction with error and delegates`() {
    val delegate = mock<RecordInterceptor<String, String>>()
    val interceptor = SentryKafkaRecordInterceptor(scopes, delegate)
    val record = createRecord()
    val exception = RuntimeException("processing failed")

    interceptor.intercept(record, consumer)
    interceptor.failure(record, exception, consumer)

    verify(delegate).failure(record, exception, consumer)
  }

  @Test
  fun `afterRecord delegates to existing interceptor`() {
    val delegate = mock<RecordInterceptor<String, String>>()
    val interceptor = SentryKafkaRecordInterceptor(scopes, delegate)
    val record = createRecord()

    interceptor.afterRecord(record, consumer)

    verify(delegate).afterRecord(record, consumer)
  }

  @Test
  fun `trace origin is set correctly`() {
    assertEquals(
      "auto.queue.spring_jakarta.kafka.consumer",
      SentryKafkaRecordInterceptor.TRACE_ORIGIN,
    )
  }

  @Test
  fun `clearThreadState cleans up stale context`() {
    val interceptor = SentryKafkaRecordInterceptor<String, String>(scopes)
    val record = createRecord()

    // intercept sets up context in ThreadLocal
    interceptor.intercept(record, consumer)

    // clearThreadState should clean up without success/failure being called
    interceptor.clearThreadState(consumer)

    // lifecycle token should have been closed
    verify(lifecycleToken).close()
  }

  @Test
  fun `clearThreadState is no-op when no context exists`() {
    val interceptor = SentryKafkaRecordInterceptor<String, String>(scopes)

    // should not throw
    interceptor.clearThreadState(consumer)
  }

  @Test
  fun `intercept cleans up stale context from previous record`() {
    val lifecycleToken2 = mock<ISentryLifecycleToken>()
    val forkedScopes2 = mock<IScopes>()
    whenever(forkedScopes2.options).thenReturn(options)
    whenever(forkedScopes2.makeCurrent()).thenReturn(lifecycleToken2)
    val tx2 = SentryTracer(TransactionContext("queue.process", "queue.process"), forkedScopes2)
    whenever(forkedScopes2.startTransaction(any<TransactionContext>(), any())).thenReturn(tx2)

    var callCount = 0
    whenever(scopes.forkedScopes(any())).thenAnswer {
      callCount++
      if (callCount == 1) {
        val forkedScopes1 = mock<IScopes>()
        whenever(forkedScopes1.options).thenReturn(options)
        whenever(forkedScopes1.makeCurrent()).thenReturn(lifecycleToken)
        val tx1 = SentryTracer(TransactionContext("queue.process", "queue.process"), forkedScopes1)
        whenever(forkedScopes1.startTransaction(any<TransactionContext>(), any())).thenReturn(tx1)
        forkedScopes1
      } else {
        forkedScopes2
      }
    }

    val interceptor = SentryKafkaRecordInterceptor<String, String>(scopes)
    val record = createRecord()

    // First intercept sets up context
    interceptor.intercept(record, consumer)

    // Second intercept without success/failure — should clean up stale context first
    interceptor.intercept(record, consumer)

    // First lifecycle token should have been closed by the defensive cleanup
    verify(lifecycleToken).close()
  }
}
