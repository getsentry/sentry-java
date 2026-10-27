package io.sentry.spring.boot.integrationtest

import com.google.common.truth.Truth.assertThat
import io.sentry.protocol.SentryTransaction
import io.sentry.test.transaction
import kotlin.test.Test
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource

/**
 * A message sent through Kafka is traced on both sides: where it's published and processed.
 *
 * Unlike the other tests, this one runs its own application context with an in-JVM broker, because
 * the shared context has to work on Spring Boot versions without Kafka support.
 */
@ActiveProfiles("kafka")
@EmbeddedKafka(topics = [KAFKA_TOPIC], bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@TestPropertySource(
  properties =
    ["sentry.enable-queue-tracing=true", "spring.kafka.consumer.auto-offset-reset=earliest"]
)
class KafkaIntegrationTest : IntegrationTest() {

  @Test
  fun `publishing a message traces a queue publish span`() {
    val producer = produce("publish-test")

    val span = producer.spans.single { it.op == "queue.publish" }
    assertThat(span.data).containsEntry("messaging.system", "kafka")
    assertThat(span.data).containsEntry("messaging.destination.name", KAFKA_TOPIC)
  }

  @Test
  fun `processing a message continues the producer's trace`() {
    val traceId = produce("process-test").contexts.trace!!.traceId

    val consumer =
      server
        .awaitEnvelope {
          it.transaction()?.contexts?.trace?.let { trace ->
            trace.operation == "queue.process" && trace.traceId == traceId
          } == true
        }
        .transaction()!!
    val data = consumer.contexts.trace!!.data
    assertThat(data).containsEntry("messaging.system", "kafka")
    assertThat(data).containsEntry("messaging.destination.name", KAFKA_TOPIC)
  }

  /** Sends [message] through the app and returns the transaction of the request that sent it. */
  private fun produce(message: String): SentryTransaction {
    assertThat(restTemplate.getForObject("/kafka/produce?message=$message", String::class.java))
      .isEqualTo("Message sent: $message")
    return server
      .awaitEnvelope {
        it.transaction()?.let { t ->
          t.transaction == "GET /kafka/produce" && t.request?.queryString == "message=$message"
        } == true
      }
      .transaction()!!
  }
}
