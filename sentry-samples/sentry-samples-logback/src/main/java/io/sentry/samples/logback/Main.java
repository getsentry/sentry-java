package io.sentry.samples.logback;

import java.util.Properties;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
  private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

  private KafkaProducer<String, String> producer;

  public Main() {
    Properties props = new Properties();
    props.put("bootstrap.servers", "localhost:9092");
    props.put("acks", "all");
    props.put("retries", 0);
    props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
    props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
    producer = new KafkaProducer<String, String>(props);
  }

  public static void main(String[] args) {
    new Main();
    //    LOGGER.debug("Hello Sentry!");
    //
    //    // MDC tags listed in logback.xml are converted to Sentry Event tags
    //    MDC.put("userId", UUID.randomUUID().toString());
    //    MDC.put("requestId", UUID.randomUUID().toString());
    //    // MDC tag not listed in logback.xml
    //    MDC.put("context-tag", "context-tag-value");
    //
    //    // logging arguments are converted to Sentry Event parameters
    //    LOGGER.info("User has made a purchase of product: {}", 445);
    //
    //    try {
    //      throw new RuntimeException("Invalid productId=445");
    //    } catch (Throwable e) {
    //      LOGGER.error("Something went wrong", e);
    //    }
  }
}
