package io.sentry.systemtest

import io.sentry.systemtest.util.TestHelper
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Before

/**
 * System tests for Kafka queue instrumentation on the OTel Jakarta noagent sample.
 *
 * The Sentry Kafka auto-configuration (`SentryKafkaQueueConfiguration`) is intentionally suppressed
 * when `io.sentry.opentelemetry.SentryAutoConfigurationCustomizerProvider` is on the classpath, so
 * the Sentry `SentryKafkaProducer` and `SentryKafkaRecordInterceptor` must not be wired.
 *
 * These tests produce a Kafka message end-to-end and assert that Sentry-style `queue.publish` /
 * `queue.process` spans/transactions are *not* emitted. Any Kafka telemetry in OTel mode must come
 * from the OTel Kafka instrumentation, not from the Sentry Kafka integration.
 *
 * Requires:
 * - The sample app running with `--spring.profiles.active=kafka`
 * - A Kafka broker at localhost:9092
 * - The mock Sentry server at localhost:8000
 */
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

    testHelper.ensureNoTransactionReceived { transaction, _ ->
      transaction.contexts.trace?.operation == "queue.process" ||
        transaction.spans.any { span -> span.op == "queue.publish" }
    }
  }
}
