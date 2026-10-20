package io.sentry.spring.boot4.agentfixture

import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

const val KAFKA_TOPIC = "sentry-topic"

@RestController
@RequestMapping("/kafka")
class KafkaController(private val kafkaTemplate: KafkaTemplate<String, String>) {
  @GetMapping("/produce")
  fun produce(@RequestParam message: String): String {
    kafkaTemplate.send(KAFKA_TOPIC, message)
    return "Message sent: $message"
  }
}

@Component
class KafkaConsumer {
  /** Does nothing with the message; Sentry traces the listener invocation. */
  @KafkaListener(topics = [KAFKA_TOPIC], groupId = "sentry-integration-test")
  fun listen(message: String) {}
}
