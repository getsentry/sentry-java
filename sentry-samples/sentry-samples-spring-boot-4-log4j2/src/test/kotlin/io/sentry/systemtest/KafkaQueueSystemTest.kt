package io.sentry.systemtest

import io.sentry.systemtest.util.TestHelper
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Before

/**
 * System tests for Kafka queue instrumentation.
 *
 * Requires:
 * - The sample app running with `--spring.profiles.active=kafka`
 * - A Kafka broker at localhost:9092
 * - The mock Sentry server at localhost:8000
 */
class KafkaQueueSystemTest {
  lateinit var testHelper: TestHelper

  @Before
  fun setup() {
    testHelper = TestHelper("http://localhost:8080")
    testHelper.reset()
  }

  @Test
  fun `producer endpoint creates queue publish span`() {
    val restClient = testHelper.restClient

    restClient.produceKafkaMessage("test-message")
    assertEquals(200, restClient.lastKnownStatusCode)

    testHelper.ensureTransactionReceived { transaction, _ ->
      testHelper.doesTransactionContainSpanWithOp(transaction, "queue.publish")
    }
  }

  @Test
  fun `consumer creates queue process transaction`() {
    val restClient = testHelper.restClient

    restClient.produceKafkaMessage("test-consumer-message")
    assertEquals(200, restClient.lastKnownStatusCode)

    // The consumer runs asynchronously, so wait for the queue.process transaction
    testHelper.ensureTransactionReceived { transaction, _ ->
      testHelper.doesTransactionHaveOp(transaction, "queue.process")
    }
  }

  @Test
  fun `producer and consumer share same trace`() {
    val restClient = testHelper.restClient

    restClient.produceKafkaMessage("trace-test-message")
    assertEquals(200, restClient.lastKnownStatusCode)

    // Capture the trace ID from the producer transaction (has queue.publish span)
    var producerTraceId: String? = null
    testHelper.ensureTransactionReceived { transaction, _ ->
      if (testHelper.doesTransactionContainSpanWithOp(transaction, "queue.publish")) {
        producerTraceId = transaction.contexts.trace?.traceId?.toString()
        true
      } else {
        false
      }
    }

    // Verify the consumer transaction has the same trace ID
    // Use retryCount=3 since the consumer may take a moment to process
    testHelper.ensureEnvelopeReceived(retryCount = 3) { envelopeString ->
      val envelope =
        testHelper.jsonSerializer.deserializeEnvelope(envelopeString.byteInputStream())
          ?: return@ensureEnvelopeReceived false
      val txItem =
        envelope.items.firstOrNull { it.header.type == io.sentry.SentryItemType.Transaction }
          ?: return@ensureEnvelopeReceived false
      val tx =
        txItem.getTransaction(testHelper.jsonSerializer) ?: return@ensureEnvelopeReceived false

      tx.contexts.trace?.operation == "queue.process" &&
        tx.contexts.trace?.traceId?.toString() == producerTraceId
    }
  }

  @Test
  fun `queue publish span has messaging attributes`() {
    val restClient = testHelper.restClient

    restClient.produceKafkaMessage("attrs-test")
    assertEquals(200, restClient.lastKnownStatusCode)

    testHelper.ensureTransactionReceived { transaction, _ ->
      val span = transaction.spans.firstOrNull { it.op == "queue.publish" }
      if (span == null) return@ensureTransactionReceived false

      val data = span.data ?: return@ensureTransactionReceived false
      data["messaging.system"] == "kafka" && data["messaging.destination.name"] == "sentry-topic"
    }
  }

  @Test
  fun `queue process transaction has messaging attributes`() {
    val restClient = testHelper.restClient

    restClient.produceKafkaMessage("process-attrs-test")
    assertEquals(200, restClient.lastKnownStatusCode)

    testHelper.ensureTransactionReceived { transaction, _ ->
      if (!testHelper.doesTransactionHaveOp(transaction, "queue.process")) {
        return@ensureTransactionReceived false
      }

      val data = transaction.contexts.trace?.data ?: return@ensureTransactionReceived false
      data["messaging.system"] == "kafka" && data["messaging.destination.name"] == "sentry-topic"
    }
  }
}
