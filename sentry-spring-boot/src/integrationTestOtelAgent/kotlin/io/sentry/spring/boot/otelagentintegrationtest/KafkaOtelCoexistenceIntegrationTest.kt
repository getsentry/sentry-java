package io.sentry.spring.boot.otelagentintegrationtest

import com.google.common.truth.Truth.assertThat
import io.sentry.protocol.SentryTransaction
import io.sentry.test.transaction
import kotlin.test.Test
import org.junit.AfterClass
import org.junit.BeforeClass
import org.springframework.kafka.test.EmbeddedKafkaBroker

/**
 * With the agent active, OpenTelemetry traces Kafka instead of Sentry's own Spring Kafka
 * integration.
 *
 * Unlike the other tests, this one starts an app of its own, with Kafka and an in-JVM broker, so
 * the shared app doesn't need a broker.
 */
class KafkaOtelCoexistenceIntegrationTest : IntegrationTest() {
  override val app: AgentApp
    get() = kafkaApp!!

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

  companion object {
    // With the fixture's KAFKA_TOPIC, so no Docker is needed.
    private val kafka = EmbeddedKafkaBroker(1, true, 1, "sentry-topic")
    // Unset if the app didn't start, in which case stopApp still has to stop the broker.
    private var kafkaApp: AgentApp? = null

    @JvmStatic
    @BeforeClass
    fun startApp() {
      kafka.afterPropertiesSet()
      kafkaApp =
        AgentApp(
          listOf(
            "--spring.profiles.active=kafka",
            "--spring.kafka.bootstrap-servers=${kafka.brokersAsString}",
          )
        )
    }

    @JvmStatic
    @AfterClass
    fun stopApp() {
      kafkaApp?.close()
      kafka.destroy()
    }
  }
}
