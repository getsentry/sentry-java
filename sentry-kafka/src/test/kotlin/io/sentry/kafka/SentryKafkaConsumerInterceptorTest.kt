package io.sentry.kafka

import io.sentry.IScopes
import io.sentry.ITransaction
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import io.sentry.test.initForTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertSame
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SentryKafkaConsumerInterceptorTest {

  @BeforeTest
  fun setup() {
    initForTest {
      it.dsn = "https://key@sentry.io/proj"
      it.isEnableQueueTracing = true
      it.tracesSampleRate = 1.0
    }
  }

  @AfterTest
  fun teardown() {
    Sentry.close()
  }

  @Test
  fun `does nothing when queue tracing is disabled`() {
    val scopes = mock<IScopes>()
    val options = SentryOptions().apply { isEnableQueueTracing = false }
    whenever(scopes.options).thenReturn(options)

    val interceptor = SentryKafkaConsumerInterceptor<String, String>(scopes)
    val records = singleRecordBatch()

    val result = interceptor.onConsume(records)

    assertSame(records, result)
    verify(scopes, never()).startTransaction(any<TransactionContext>(), any<TransactionOptions>())
  }

  @Test
  fun `starts and finishes queue receive transaction for consumed batch`() {
    val scopes = mock<IScopes>()
    val options = SentryOptions().apply { isEnableQueueTracing = true }
    val transaction = mock<ITransaction>()

    whenever(scopes.options).thenReturn(options)
    whenever(scopes.continueTrace(any(), any())).thenReturn(null)
    whenever(scopes.startTransaction(any<TransactionContext>(), any<TransactionOptions>()))
      .thenReturn(transaction)
    whenever(transaction.isNoOp).thenReturn(false)

    val interceptor = SentryKafkaConsumerInterceptor<String, String>(scopes)

    interceptor.onConsume(singleRecordBatch())

    verify(scopes).startTransaction(any<TransactionContext>(), any<TransactionOptions>())
    verify(transaction).setData("messaging.system", "kafka")
    verify(transaction).setData("messaging.destination.name", "my-topic")
    verify(transaction).setData("messaging.batch.message.count", 1)
    verify(transaction).finish()
  }

  @Test
  fun `commit callback is no-op`() {
    val interceptor = SentryKafkaConsumerInterceptor<String, String>(mock())

    interceptor.onCommit(mapOf(TopicPartition("my-topic", 0) to OffsetAndMetadata(1)))
  }

  @Test
  fun `no-arg constructor uses current scopes`() {
    val interceptor = SentryKafkaConsumerInterceptor<String, String>()
    val records = singleRecordBatch()

    val result = interceptor.onConsume(records)

    assertSame(records, result)
  }

  private fun singleRecordBatch(): ConsumerRecords<String, String> {
    val partition = TopicPartition("my-topic", 0)
    val record = ConsumerRecord("my-topic", 0, 0L, "key", "value")
    return ConsumerRecords(mapOf(partition to listOf(record)))
  }
}
