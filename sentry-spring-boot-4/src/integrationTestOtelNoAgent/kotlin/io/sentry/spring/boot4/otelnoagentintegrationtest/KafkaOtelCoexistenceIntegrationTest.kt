package io.sentry.spring.boot4.otelnoagentintegrationtest

import com.google.common.truth.Truth.assertThat
import io.sentry.protocol.SentryTransaction
import io.sentry.test.transaction
import kotlin.test.Test

/** With OpenTelemetry active, it traces Kafka instead of Sentry's own Spring Kafka integration. */
class KafkaOtelCoexistenceIntegrationTest : IntegrationTest() {

  @Test
  fun `OpenTelemetry traces both sides of a message instead of Sentry's Kafka integration`() {
    val message = "otel-coexistence-test"
    assertThat(restTemplate.getForObject("/kafka/produce?message=$message", String::class.java))
      .isEqualTo("Message sent: $message")

    val producer =
      server
        .awaitEnvelope {
          it.transaction()?.let { t ->
            t.transaction == "GET /kafka/produce" && t.request?.queryString == "message=$message"
          } == true
        }
        .transaction()!!
    assertTracedByOpenTelemetry(producer)

    val traceId = producer.contexts.trace!!.traceId
    val consumer =
      server
        .awaitEnvelope {
          it.transaction()?.contexts?.trace?.let { trace ->
            trace.operation == "queue.process" && trace.traceId == traceId
          } == true
        }
        .transaction()!!
    assertTracedByOpenTelemetry(consumer)
    assertThat(consumer.contexts.trace!!.data).containsEntry("messaging.system", "kafka")
  }

  private fun assertTracedByOpenTelemetry(transaction: SentryTransaction) {
    assertThat(transaction.contexts.trace!!.origin).isEqualTo("auto.opentelemetry")
    assertThat(transaction.sdk?.integrationSet.orEmpty()).doesNotContain("SpringKafka")
  }
}
