package io.sentry.spring.jakarta.kafka;

import io.sentry.ScopesAdapter;
import io.sentry.kafka.SentryKafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.core.ProducerPostProcessor;

/**
 * Installs a {@link ProducerPostProcessor} on every {@link ProducerFactory} bean so that each
 * {@link Producer} created by Spring Kafka is wrapped via {@link SentryKafkaProducer#wrap
 * SentryKafkaProducer.wrap(Producer)}.
 *
 * <p>The wrapper records a {@code queue.publish} span around each {@code send(...)} that finishes
 * when the broker ack callback fires, giving a real producer-send lifecycle span. {@code
 * KafkaTemplate} beans are left untouched, so all customer-configured listeners, interceptors and
 * observation settings are preserved.
 *
 * <p>Idempotent: re-running on the same factory does not register the post-processor twice.
 *
 * <p>Note: {@link ProducerFactory#addPostProcessor(ProducerPostProcessor)} is a default method on
 * the interface. Custom factories that do not extend {@code DefaultKafkaProducerFactory} and do not
 * implement {@code addPostProcessor} will silently no-op.
 */
@ApiStatus.Internal
public final class SentryKafkaProducerBeanPostProcessor
    implements BeanPostProcessor, PriorityOrdered {

  @Override
  @SuppressWarnings({"unchecked", "rawtypes"})
  public @NotNull Object postProcessAfterInitialization(
      final @NotNull Object bean, final @NotNull String beanName) throws BeansException {
    if (bean instanceof ProducerFactory) {
      final @NotNull ProducerFactory factory = (ProducerFactory) bean;

      for (final Object existing : factory.getPostProcessors()) {
        if (existing instanceof SentryProducerPostProcessor) {
          return bean;
        }
      }

      factory.addPostProcessor(new SentryProducerPostProcessor<>());
    }
    return bean;
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }

  /**
   * Marker {@link ProducerPostProcessor} that wraps the freshly created Kafka {@link Producer} via
   * {@link SentryKafkaProducer#wrap}.
   */
  static final class SentryProducerPostProcessor<K, V> implements ProducerPostProcessor<K, V> {
    @Override
    public @NotNull Producer<K, V> apply(final @NotNull Producer<K, V> producer) {
      return SentryKafkaProducer.wrap(
          producer, ScopesAdapter.getInstance(), "auto.queue.spring_jakarta.kafka.producer");
    }
  }
}
