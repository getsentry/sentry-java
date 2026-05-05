package io.sentry.systemtest

import io.sentry.systemtest.util.TestHelper
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Before

class KafkaOtelCoexistenceSystemTest {
  lateinit var testHelper: TestHelper

  @Before
  fun setup() {
    testHelper = TestHelper("http://localhost:8080")
    testHelper.reset()
  }

  @Test
  fun `Sentry Kafka integration is suppressed when OTel is active`() {
    val restClient = testHelper.restClient

    restClient.produceKafkaMessage("otel-coexistence-test")
    assertEquals(200, restClient.lastKnownStatusCode)

    testHelper.ensureTransactionReceived { transaction, _ ->
      transaction.transaction == "GET /kafka/produce" &&
        transaction.sdk?.integrationSet?.contains("SpringKafka") != true &&
        transaction.spans.any { span ->
          span.op == "queue.publish" &&
            span.origin == "auto.opentelemetry" &&
            span.data?.get("messaging.system") == "kafka"
        }
    }

    testHelper.ensureTransactionReceived { transaction, _ ->
      transaction.contexts.trace?.operation == "queue.process" &&
        transaction.contexts.trace?.origin == "auto.opentelemetry" &&
        transaction.contexts.trace?.data?.get("messaging.system") == "kafka" &&
        transaction.sdk?.integrationSet?.contains("SpringKafka") != true
    }
  }
}
