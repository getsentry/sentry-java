package io.sentry.spring.boot.otelnoagentintegrationtest

import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

const val KAFKA_TOPIC = "sentry-topic"

// The beans belong to a profile, so only KafkaOtelCoexistenceIntegrationTest's context needs a
// broker.

@RestController
@Profile("kafka")
@RequestMapping("/kafka")
open class KafkaController(private val kafkaTemplate: KafkaTemplate<String, String>) {
  @GetMapping("/produce")
  open fun produce(@RequestParam message: String): String {
    kafkaTemplate.send(KAFKA_TOPIC, message)
    return "Message sent: $message"
  }
}

@Component
@Profile("kafka")
open class KafkaConsumer {
  /** Does nothing with the message; Sentry traces the listener invocation. */
  @KafkaListener(topics = [KAFKA_TOPIC], groupId = "sentry-integration-test")
  open fun listen(message: String) {}
}
