package io.sentry.kafka

import com.google.common.truth.Truth.assertThat
import io.sentry.Sentry
import io.sentry.test.MockSentryServer
import io.sentry.test.initForTest
import io.sentry.test.transaction
import java.time.Duration
import java.util.Properties
import kotlin.test.AfterTest
import kotlin.test.Test
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker

/**
 * Sends a message through the plain Kafka client to an in-JVM KRaft broker, so no Docker is needed,
 * and checks that both sides reach Sentry over the real HTTP transport.
 */
class SentryKafkaEndToEndTest {
  private val server = MockSentryServer()
  private val kafka = EmbeddedKafkaKraftBroker(1, 1, TOPIC).apply { afterPropertiesSet() }

  @AfterTest
  fun teardown() {
    Sentry.close()
    kafka.destroy()
    server.close()
  }

  @Test
  fun `a message is traced where it's published and where it's processed`() {
    initForTest {
      it.dsn = server.dsn
      it.tracesSampleRate = 1.0
      it.isEnableQueueTracing = true
    }

    val transaction = Sentry.startTransaction("kafka-demo", "demo")
    transaction.makeCurrent().use {
      SentryKafkaProducer.wrap(KafkaProducer<String, String>(producerProperties())).use { producer
        ->
        producer.send(ProducerRecord(TOPIC, "sentry-kafka message")).get()
      }
    }
    transaction.finish()

    KafkaConsumer<String, String>(consumerProperties()).use { consumer ->
      consumer.subscribe(listOf(TOPIC))
      val record = pollOne(consumer)
      SentryKafkaConsumerTracing.withTracing(record, Runnable {})
    }

    val producer =
      server.awaitEnvelope { it.transaction()?.transaction == "kafka-demo" }.transaction()!!
    assertThat(producer.spans.map { it.op }).contains("queue.publish")

    val consumer =
      server
        .awaitEnvelope { it.transaction()?.contexts?.trace?.operation == "queue.process" }
        .transaction()!!
    val trace = consumer.contexts.trace!!
    assertThat(trace.origin).isEqualTo(SentryKafkaConsumerTracing.TRACE_ORIGIN)
    assertThat(trace.data).containsEntry("messaging.system", "kafka")
    // The producer's span puts the trace in the message's headers, and the consumer continues it.
    assertThat(trace.traceId).isEqualTo(producer.contexts.trace!!.traceId)
  }

  private fun pollOne(consumer: KafkaConsumer<String, String>): ConsumerRecord<String, String> {
    repeat(20) {
      consumer.poll(Duration.ofMillis(500)).firstOrNull()?.let {
        return it
      }
    }
    error("No message arrived")
  }

  private fun producerProperties(): Properties =
    Properties().apply {
      put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.brokersAsString)
      put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
      put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
    }

  private fun consumerProperties(): Properties =
    Properties().apply {
      put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.brokersAsString)
      put(ConsumerConfig.GROUP_ID_CONFIG, "sentry-kafka-end-to-end-test")
      put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
      put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
      put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
    }

  private companion object {
    const val TOPIC = "sentry-topic"
  }
}
