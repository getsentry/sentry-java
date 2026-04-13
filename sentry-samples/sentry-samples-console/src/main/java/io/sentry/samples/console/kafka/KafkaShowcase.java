package io.sentry.samples.console.kafka;

import io.sentry.ISentryLifecycleToken;
import io.sentry.ITransaction;
import io.sentry.Sentry;
import io.sentry.kafka.SentryKafkaConsumerInterceptor;
import io.sentry.kafka.SentryKafkaProducerInterceptor;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

public final class KafkaShowcase {

  public static final String TOPIC = "sentry-topic-console-sample";

  private KafkaShowcase() {}

  public static void demonstrate(final String bootstrapServers) {
    final CountDownLatch consumedLatch = new CountDownLatch(1);
    final Thread consumerThread = startKafkaConsumerThread(bootstrapServers, consumedLatch);

    final Properties producerProperties = getProducerProperties(bootstrapServers);

    final ITransaction transaction = Sentry.startTransaction("kafka-demo", "demo");
    try (ISentryLifecycleToken ignored = transaction.makeCurrent()) {
      try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProperties)) {
        Thread.sleep(500);
        producer.send(new ProducerRecord<>(TOPIC, "sentry-kafka sample message")).get();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } catch (Exception ignoredException) {
        // local broker may not be available when running the sample
      }

      try {
        consumedLatch.await(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    } finally {
      consumerThread.interrupt();
      try {
        consumerThread.join(1000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      transaction.finish();
    }
  }

  private static Thread startKafkaConsumerThread(
      final String bootstrapServers, final CountDownLatch consumedLatch) {
    final Thread consumerThread =
        new Thread(
            () -> {
              final Properties consumerProperties = getConsumerProperties(bootstrapServers);

              try (KafkaConsumer<String, String> consumer =
                  new KafkaConsumer<>(consumerProperties)) {
                consumer.subscribe(Collections.singletonList(topic));

                while (!Thread.currentThread().isInterrupted() && consumedLatch.getCount() > 0) {
                  final ConsumerRecords<String, String> records =
                      consumer.poll(Duration.ofMillis(500));
                  if (!records.isEmpty()) {
                    consumedLatch.countDown();
                    break;
                  }
                }
              } catch (Exception ignored) {
                // local broker may not be available when running the sample
              }
            },
            "sentry-kafka-sample-consumer");
    consumerThread.start();
    return consumerThread;
  }

  private static Properties getConsumerProperties(String bootstrapServers) {
    final Properties consumerProperties = new Properties();

    consumerProperties.put(
      ConsumerConfig.INTERCEPTOR_CLASSES_CONFIG,
      SentryKafkaConsumerInterceptor.class.getName());

    consumerProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    consumerProperties.put(
        ConsumerConfig.GROUP_ID_CONFIG, "sentry-console-sample-" + UUID.randomUUID());
    consumerProperties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    consumerProperties.put(
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    consumerProperties.put(
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
        StringDeserializer.class.getName());
    consumerProperties.put(ConsumerConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 2000);
    consumerProperties.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, 2000);

    return consumerProperties;
  }

  private static Properties getProducerProperties(String bootstrapServers) {
    final Properties producerProperties = new Properties();

    producerProperties.put(
      ProducerConfig.INTERCEPTOR_CLASSES_CONFIG, SentryKafkaProducerInterceptor.class.getName());

    producerProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    producerProperties.put(
      ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    producerProperties.put(
      ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    producerProperties.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 2000);
    producerProperties.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 2000);
    producerProperties.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 3000);

    return producerProperties;
  }
}
