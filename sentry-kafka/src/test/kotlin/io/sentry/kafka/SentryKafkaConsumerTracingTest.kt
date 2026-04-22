package io.sentry.kafka

import io.sentry.BaggageHeader
import io.sentry.IScopes
import io.sentry.ISentryLifecycleToken
import io.sentry.ITransaction
import io.sentry.SentryOptions
import io.sentry.SentryTraceHeader
import io.sentry.SpanDataConvention
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Optional
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.record.TimestampType
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SentryKafkaConsumerTracingTest {

  private lateinit var scopes: IScopes
  private lateinit var forkedScopes: IScopes
  private lateinit var options: SentryOptions
  private lateinit var lifecycleToken: ISentryLifecycleToken
  private lateinit var transaction: ITransaction
  private lateinit var tracing: SentryKafkaConsumerTracing

  @BeforeTest
  fun setup() {
    scopes = mock()
    forkedScopes = mock()
    lifecycleToken = mock()
    transaction = mock()
    tracing = SentryKafkaConsumerTracing(scopes)

    options =
      SentryOptions().apply {
        dsn = "https://key@sentry.io/proj"
        isEnableQueueTracing = true
        tracesSampleRate = 1.0
      }

    whenever(scopes.options).thenReturn(options)
    whenever(scopes.forkedRootScopes(any())).thenReturn(forkedScopes)
    whenever(forkedScopes.options).thenReturn(options)
    whenever(forkedScopes.makeCurrent()).thenReturn(lifecycleToken)
    whenever(forkedScopes.startTransaction(any<TransactionContext>(), any<TransactionOptions>()))
      .thenReturn(transaction)
    whenever(transaction.isNoOp).thenReturn(false)
  }

  @Test
  fun `withTracing creates queue process transaction with record metadata`() {
    val sentryTraceValue = "2722d9f6ec019ade60c776169d9a8904-cedf5b7571cb4972-1"
    val baggageValue = "sentry-sample_rate=1"
    val record =
      createRecord(
        sentryTrace = sentryTraceValue,
        baggage = baggageValue,
        messageId = "message-123",
        deliveryAttempt = 3,
        enqueuedTime = (System.currentTimeMillis() / 1000.0 - 1.0).toString(),
        serializedValueSize = 5,
      )

    val txContextCaptor = argumentCaptor<TransactionContext>()
    val txOptionsCaptor = argumentCaptor<TransactionOptions>()

    val result = tracing.withTracingImpl(record, Callable { "done" })

    assertEquals("done", result)
    verify(scopes).forkedRootScopes("SentryKafkaConsumerTracing")
    verify(forkedScopes).makeCurrent()
    verify(forkedScopes).continueTrace(eq(sentryTraceValue), eq(listOf(baggageValue)))
    verify(forkedScopes).startTransaction(txContextCaptor.capture(), txOptionsCaptor.capture())

    assertEquals("queue.process", txContextCaptor.firstValue.name)
    assertEquals("queue.process", txContextCaptor.firstValue.operation)
    assertEquals(SentryKafkaConsumerTracing.TRACE_ORIGIN, txOptionsCaptor.firstValue.origin)
    assertTrue(txOptionsCaptor.firstValue.isBindToScope)

    verify(transaction).setData(SpanDataConvention.MESSAGING_SYSTEM, "kafka")
    verify(transaction).setData(SpanDataConvention.MESSAGING_DESTINATION_NAME, "my-topic")
    verify(transaction).setData(SpanDataConvention.MESSAGING_MESSAGE_ID, "message-123")
    verify(transaction).setData(SpanDataConvention.MESSAGING_MESSAGE_BODY_SIZE, 5)
    verify(transaction).setData(SpanDataConvention.MESSAGING_MESSAGE_RETRY_COUNT, 2)
    verify(transaction)
      .setData(
        eq(SpanDataConvention.MESSAGING_MESSAGE_RECEIVE_LATENCY),
        check<Long> { assertTrue(it >= 0) },
      )
    verify(transaction).setStatus(SpanStatus.OK)
    verify(transaction).finish()
    verify(lifecycleToken).close()
  }

  @Test
  fun `withTracing passes all baggage headers to continueTrace`() {
    val sentryTraceValue = "2722d9f6ec019ade60c776169d9a8904-cedf5b7571cb4972-1"
    val record =
      createRecord(
        sentryTrace = sentryTraceValue,
        baggageHeaders = listOf("third=party", "sentry-sample_rate=1"),
      )

    tracing.withTracingImpl(record, Callable { "done" })

    verify(forkedScopes)
      .continueTrace(eq(sentryTraceValue), eq(listOf("third=party", "sentry-sample_rate=1")))
  }

  @Test
  fun `withTracing skips scope forking when queue tracing is disabled`() {
    options.isEnableQueueTracing = false
    val record = createRecord()

    val result = tracing.withTracingImpl(record, Callable { "done" })

    assertEquals("done", result)
    verify(scopes, never()).forkedRootScopes(any<String>())
  }

  @Test
  fun `withTracing skips scope forking when origin is ignored`() {
    options.setIgnoredSpanOrigins(listOf(SentryKafkaConsumerTracing.TRACE_ORIGIN))
    val record = createRecord()

    val result = tracing.withTracingImpl(record, Callable { "done" })

    assertEquals("done", result)
    verify(scopes, never()).forkedRootScopes(any<String>())
  }

  @Test
  fun `withTracing marks transaction as error when callback throws`() {
    val record = createRecord()
    val exception = RuntimeException("boom")

    val thrown =
      assertFailsWith<RuntimeException> {
        tracing.withTracingImpl(record, Callable<String> { throw exception })
      }

    assertEquals(exception, thrown)
    verify(transaction).setStatus(SpanStatus.INTERNAL_ERROR)
    verify(transaction).setThrowable(exception)
    verify(transaction).finish()
    verify(lifecycleToken).close()
  }

  @Test
  fun `withTracing falls back to direct callback execution when instrumentation setup fails`() {
    whenever(scopes.forkedRootScopes(any<String>()))
      .thenThrow(RuntimeException("broken instrumentation"))
    val record = createRecord()

    val result = tracing.withTracingImpl(record, Callable { "done" })

    assertEquals("done", result)
    verify(forkedScopes, never()).makeCurrent()
    verify(transaction, never()).finish()
  }

  @Test
  fun `withTracing runnable overload executes callback`() {
    val record = createRecord()
    val didRun = AtomicBoolean(false)

    tracing.withTracingImpl(record, Runnable { didRun.set(true) })

    assertTrue(didRun.get())
    verify(transaction).setStatus(SpanStatus.OK)
    verify(transaction).finish()
  }

  @Test
  fun `withTracing runnable overload preserves original throwable`() {
    val record = createRecord()
    val exception = IOException("boom")

    val thrown =
      assertFailsWith<IOException> { tracing.withTracingImpl(record, Runnable { throw exception }) }

    assertEquals(exception, thrown)
    verify(transaction).setStatus(SpanStatus.INTERNAL_ERROR)
    verify(transaction).setThrowable(exception)
    verify(transaction).finish()
  }

  private fun createRecord(
    topic: String = "my-topic",
    sentryTrace: String? = null,
    baggage: String? = null,
    baggageHeaders: List<String>? = null,
    messageId: String? = null,
    deliveryAttempt: Int? = null,
    enqueuedTime: String? = null,
    serializedValueSize: Int = -1,
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
    messageId?.let {
      headers.add(SpanDataConvention.MESSAGING_MESSAGE_ID, it.toByteArray(StandardCharsets.UTF_8))
    }
    deliveryAttempt?.let {
      headers.add("kafka_deliveryAttempt", ByteBuffer.allocate(Int.SIZE_BYTES).putInt(it).array())
    }
    enqueuedTime?.let {
      headers.add(
        SentryKafkaProducerInterceptor.SENTRY_ENQUEUED_TIME_HEADER,
        it.toByteArray(StandardCharsets.UTF_8),
      )
    }

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
}
