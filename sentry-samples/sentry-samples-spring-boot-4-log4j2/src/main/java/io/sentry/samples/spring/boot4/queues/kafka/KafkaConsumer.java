package io.sentry.samples.spring.boot4.queues.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Profile("kafka")
public class KafkaConsumer {

  private static final Logger logger = LoggerFactory.getLogger(KafkaConsumer.class);

  @KafkaListener(topics = "sentry-topic", groupId = "sentry-sample-group")
  public void listen(String message) {
    logger.info("Received message: {}", message);
  }
}
