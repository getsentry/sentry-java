package io.sentry.spring7.kafka

import io.sentry.BaggageHeader
import io.sentry.IScopes
import io.sentry.ISentryLifecycleToken
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.SentryTraceHeader
import io.sentry.SentryTracer
import io.sentry.SpanDataConvention
import io.sentry.TransactionContext
import io.sentry.kafka.SentryKafkaProducer
import io.sentry.test.initForTest
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Optional
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.record.TimestampType
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.kafka.listener.RecordInterceptor
import org.springframework.kafka.support.KafkaHeaders

class SentryKafkaRecordInterceptorTest {

  private lateinit var scopes: IScopes
  private lateinit var forkedScopes: IScopes
  private lateinit var options: SentryOptions
  private lateinit var consumer: Consumer<String, String>
  private lateinit var lifecycleToken: ISentryLifecycleToken
  private lateinit var transaction: SentryTracer

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

    forkedScopes = mock()
    whenever(scopes.forkedRootScopes(any())).thenReturn(forkedScopes)
    whenever(forkedScopes.options).thenReturn(options)
    whenever(forkedScopes.makeCurrent()).thenReturn(lifecycleToken)

    transaction = SentryTracer(TransactionContext("queue.process", "queue.process"), forkedScopes)
    whenever(forkedScopes.startTransaction(any<TransactionContext>(), any()))
      .thenReturn(transaction)
  }

  @AfterTest
  fun teardown() {
    Sentry.close()
  }

  private fun createRecord(
    topic: String = "my-topic",
    headers: RecordHeaders = RecordHeaders(),
    serializedValueSize: Int = -1,
  ): ConsumerRecord<String, String> {
    return ConsumerRecord(
      topic,
      0,
      0L,
      System.currentTimeMillis(),
      TimestampType.CREATE_TIME,
      3,
      serializedValueSize,
      "key",
      "value",
      headers,
      Optional.empty(),
    )
  }

  private fun createRecordWithHeaders(
    sentryTrace: String? = null,
    baggage: String? = null,
    baggageHeaders: List<String>? = null,
    enqueuedTime: String? = null,
    deliveryAttempt: Int? = null,
  ): ConsumerRecord<String, String> {
    val headers = RecordHeaders()
    sentryTrace?.let {
      headers.add(SentryTraceHeader.SENTRY_TRACE_HEADER, it.toByteArray(StandardCharsets.UTF_8))
    }
    baggage?.let {
      headers.add(BaggageHeader.BAGGAGE_HEADER, it.toByteArray(StandardCharsets.UTF_8))
    }
    baggageHeaders?.forEach {
      headers.add(BaggageHeader.BAGGAGE_HEADER, it.toByteArray(StandardCharsets.UTF_8))
    }
    enqueuedTime?.let {
      headers.add(
        SentryKafkaProducer.SENTRY_ENQUEUED_TIME_HEADER,
        it.toByteArray(StandardCharsets.UTF_8),
      )
    }
    deliveryAttempt?.let {
      headers.add(
        KafkaHeaders.DELIVERY_ATTEMPT,
        ByteBuffer.allocate(Int.SIZE_BYTES).putInt(it).array(),
      )
    }
    val record = ConsumerRecord("my-topic", 0, 0L, "key", "value")
    headers.forEach { record.headers().add(it) }
    return record
  }

  @Test
  fun `intercept forks root scopes`() {
    val interceptor = SentryKafkaRecordInterceptor<String, String>(scopes)
    val record = createRecord()

    interceptor.intercept(record, consumer)

    verify(scopes).forkedRootScopes("SentryKafkaRecordInterceptor")
    verify(forkedScopes).makeCurrent()
    verify(forkedScopes)
      .startTransaction(
        org.mockito.kotlin.check<TransactionContext> {
          assertEquals("my-topic", it.name)
          assertEquals("queue.process", it.operation)
        },
        any(),
      )
  }

  @Test
  fun `intercept continues trace from headers`() {
    val interceptor = SentryKafkaRecordInterceptor<String, String>(scopes)
    val sentryTraceValue = "2722d9f6ec019ade60c776169d9a8904-cedf5b7571cb4972-1"
    val record = createRecordWithHeaders(sentryTrace = sentryTraceValue)

    interceptor.intercept(record, consumer)

    verify(forkedScopes)
      .continueTrace(org.mockito.kotlin.eq(sentryTraceValue), org.mockito.kotlin.isNull())
  }

  @Test
  fun `intercept calls continueTrace with null when no headers`() {
    val interceptor = SentryKafkaRecordInterceptor<String, String>(scopes)
    val record = createRecord()

    interceptor.intercept(record, consumer)

    verify(forkedScopes).continueTrace(org.mockito.kotlin.isNull(), org.mockito.kotlin.isNull())
  }

  @Test
  fun `intercept passes all baggage headers to continueTrace`() {
    val interceptor = SentryKafkaRecordInterceptor<String, String>(scopes)
    val sentryTraceValue = "2722d9f6ec019ade60c776169d9a8904-cedf5b7571cb4972-1"
    val record =
      createRecordWithHeaders(
        sentryTrace = sentryTraceValue,
        baggageHeaders = listOf("third=party", "sentry-sample_rate=1"),
      )

    interceptor.intercept(record, consumer)

    verify(forkedScopes)
      .continueTrace(
        org.mockito.kotlin.eq(sentryTraceValue),
        org.mockito.kotlin.eq(listOf("third=party", "sentry-sample_rate=1")),
      )
  }

  @Test
  fun `sets body size from serializedValueSize`() {
    val interceptor = SentryKafkaRecordInterceptor<String, String>(scopes)
    val record = createRecord(serializedValueSize = 42)

    interceptor.intercept(record, consumer)

    assertEquals(42, transaction.data?.get(SpanDataConvention.MESSAGING_MESSAGE_BODY_SIZE))
  }

  @Test
  fun `does not set body size when serializedValueSize is negative`() {
    val interceptor = SentryKafkaRecordInterceptor<String, String>(scopes)
    val record = createRecord(serializedValueSize = -1)

    interceptor.intercept(record, consumer)

    assertNull(transaction.data?.get(SpanDataConvention.MESSAGING_MESSAGE_BODY_SIZE))
  }

  @Test
  fun `sets retry count from delivery attempt header`() {
    val interceptor = SentryKafkaRecordInterceptor<String, String>(scopes)
    val record = createRecordWithHeaders(deliveryAttempt = 3)

    interceptor.intercept(record, consumer)

    assertEquals(2, transaction.data?.get(SpanDataConvention.MESSAGING_MESSAGE_RETRY_COUNT))
  }

  @Test
  fun `does not set retry count when delivery attempt header is missing`() {
    val interceptor = SentryKafkaRecordInterceptor<String, String>(scopes)
    val record = createRecord()

    interceptor.intercept(record, consumer)

    assertNull(transaction.data?.get(SpanDataConvention.MESSAGING_MESSAGE_RETRY_COUNT))
  }

  @Test
  fun `sets receive latency from enqueued time in epoch seconds`() {
    val interceptor = SentryKafkaRecordInterceptor<String, String>(scopes)
    val enqueuedTime = (System.currentTimeMillis() / 1000.0 - 1.0).toString()
    val record = createRecordWithHeaders(enqueuedTime = enqueuedTime)

    interceptor.intercept(record, consumer)

    val latency = transaction.data?.get(SpanDataConvention.MESSAGING_MESSAGE_RECEIVE_LATENCY)
    assertTrue(latency is Long && latency >= 0)
  }

  @Test
  fun `does not create span when queue tracing is disabled`() {
    options.isEnableQueueTracing = false
    val interceptor = SentryKafkaRecordInterceptor<String, String>(scopes)
    val record = createRecord()

    val result = interceptor.intercept(record, consumer)

    verify(scopes, never()).forkedRootScopes(any())
    verify(forkedScopes, never()).makeCurrent()
    assertEquals(record, result)
  }

  @Test
  fun `does not create span when origin is ignored`() {
    options.setIgnoredSpanOrigins(listOf(SentryKafkaRecordInterceptor.TRACE_ORIGIN))
    val interceptor = SentryKafkaRecordInterceptor<String, String>(scopes)
    val record = createRecord()

    val result = interceptor.intercept(record, consumer)

    verify(scopes, never()).forkedRootScopes(any())
    verify(forkedScopes, never()).makeCurrent()
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
    assertEquals("auto.queue.spring7.kafka.consumer", SentryKafkaRecordInterceptor.TRACE_ORIGIN)
  }

  @Test
  fun `clearThreadState cleans up stale context`() {
    val interceptor = SentryKafkaRecordInterceptor<String, String>(scopes)
    val record = createRecord()

    interceptor.intercept(record, consumer)

    interceptor.clearThreadState(consumer)

    verify(lifecycleToken).close()
  }

  @Test
  fun `clearThreadState is no-op when no context exists`() {
    val interceptor = SentryKafkaRecordInterceptor<String, String>(scopes)

    // should not throw
    interceptor.clearThreadState(consumer)
  }

  @Test
  fun `setupThreadState delegates to existing interceptor`() {
    val delegate = mock<RecordInterceptor<String, String>>()
    val interceptor = SentryKafkaRecordInterceptor(scopes, delegate)

    interceptor.setupThreadState(consumer)

    verify(delegate).setupThreadState(consumer)
  }

  @Test
  fun `setupThreadState is no-op without delegate`() {
    val interceptor = SentryKafkaRecordInterceptor<String, String>(scopes)

    // should not throw
    interceptor.setupThreadState(consumer)
  }

  @Test
  fun `clearThreadState delegates to existing interceptor`() {
    val delegate = mock<RecordInterceptor<String, String>>()
    val interceptor = SentryKafkaRecordInterceptor(scopes, delegate)

    interceptor.clearThreadState(consumer)

    verify(delegate).clearThreadState(consumer)
  }

  @Test
  fun `clearThreadState delegates to existing interceptor even when sentry cleanup throws`() {
    val delegate = mock<RecordInterceptor<String, String>>()
    whenever(lifecycleToken.close()).thenThrow(RuntimeException("boom"))
    val interceptor = SentryKafkaRecordInterceptor(scopes, delegate)
    val record = createRecord()

    interceptor.intercept(record, consumer)

    try {
      interceptor.clearThreadState(consumer)
    } catch (ignored: RuntimeException) {
      // expected
    }

    verify(delegate).clearThreadState(consumer)
  }

  @Test
  fun `full lifecycle intercept success clearThreadState closes token exactly once`() {
    val delegate = mock<RecordInterceptor<String, String>>()
    val record = createRecord()
    whenever(delegate.intercept(record, consumer)).thenReturn(record)
    val interceptor = SentryKafkaRecordInterceptor(scopes, delegate)

    interceptor.setupThreadState(consumer)
    interceptor.intercept(record, consumer)
    interceptor.success(record, consumer)
    interceptor.clearThreadState(consumer)

    // token closed once by success(); clearThreadState must not re-close it
    verify(lifecycleToken, times(1)).close()
    assertTrue(transaction.isFinished)
    // delegate hooks still delegated across the full lifecycle
    verify(delegate).setupThreadState(consumer)
    verify(delegate).success(record, consumer)
    verify(delegate).clearThreadState(consumer)
  }

  @Test
  fun `when delegate intercept returns null clearThreadState still finishes transaction and closes token`() {
    val delegate = mock<RecordInterceptor<String, String>>()
    val record = createRecord()
    // delegate filters the record — per Spring Kafka contract, success/failure will not be invoked
    whenever(delegate.intercept(record, consumer)).thenReturn(null)
    val interceptor = SentryKafkaRecordInterceptor(scopes, delegate)

    interceptor.setupThreadState(consumer)
    val result = interceptor.intercept(record, consumer)
    interceptor.clearThreadState(consumer)

    assertNull(result)
    verify(lifecycleToken, times(1)).close()
    assertTrue(transaction.isFinished)
    verify(delegate).clearThreadState(consumer)
  }

  @Test
  fun `when delegate intercept throws clearThreadState still finishes transaction and closes token`() {
    val delegate = mock<RecordInterceptor<String, String>>()
    val record = createRecord()
    val boom = RuntimeException("delegate boom")
    whenever(delegate.intercept(record, consumer)).thenThrow(boom)
    val interceptor = SentryKafkaRecordInterceptor(scopes, delegate)

    interceptor.setupThreadState(consumer)
    val thrown = assertFailsWith<RuntimeException> { interceptor.intercept(record, consumer) }
    assertEquals(boom, thrown)

    interceptor.clearThreadState(consumer)

    verify(lifecycleToken, times(1)).close()
    assertTrue(transaction.isFinished)
    verify(delegate).clearThreadState(consumer)
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

    val interceptor = SentryKafkaRecordInterceptor<String, String>(scopes)
    val record = createRecord()

    whenever(scopes.forkedRootScopes(any())).thenAnswer {
      callCount++
      if (callCount == 1) forkedScopes else forkedScopes2
    }

    // First intercept sets up context
    interceptor.intercept(record, consumer)

    // Second intercept without success/failure — should clean up stale context first
    interceptor.intercept(record, consumer)

    // First lifecycle token should have been closed by the defensive cleanup
    verify(lifecycleToken).close()
  }
}
