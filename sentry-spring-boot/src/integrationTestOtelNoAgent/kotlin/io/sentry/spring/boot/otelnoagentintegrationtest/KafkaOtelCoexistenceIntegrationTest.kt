package io.sentry.spring.boot.otelnoagentintegrationtest

import com.google.common.truth.Truth.assertThat
import io.sentry.protocol.SentryTransaction
import io.sentry.test.transaction
import kotlin.test.Test
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource

/**
 * With OpenTelemetry active, it traces Kafka instead of Sentry's own Spring Kafka integration.
 *
 * Unlike the other tests, this one adds Kafka and an in-JVM broker to its application context, so
 * the others don't need a broker.
 */
@ActiveProfiles("kafka")
@EmbeddedKafka(topics = [KAFKA_TOPIC], bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@TestPropertySource(
  properties =
    ["sentry.enable-queue-tracing=true", "spring.kafka.consumer.auto-offset-reset=earliest"]
)
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
