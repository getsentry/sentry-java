package io.sentry.samples.spring.boot4.queues.kafka;

import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("kafka")
@RequestMapping("/kafka")
public class KafkaController {

  private final KafkaTemplate<String, String> kafkaTemplate;

  public KafkaController(KafkaTemplate<String, String> kafkaTemplate) {
    this.kafkaTemplate = kafkaTemplate;
  }

  @GetMapping("/produce")
  String produce(@RequestParam(defaultValue = "hello from sentry!") String message) {
    kafkaTemplate.send("sentry-topic", message);
    return "Message sent: " + message;
  }
}
