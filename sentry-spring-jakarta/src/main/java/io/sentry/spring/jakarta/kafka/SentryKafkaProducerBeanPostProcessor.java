package io.sentry.spring.jakarta.kafka;

import io.sentry.ScopesAdapter;
import io.sentry.SentryLevel;
import io.sentry.kafka.SentryKafkaProducerInterceptor;
import java.lang.reflect.Field;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.CompositeProducerInterceptor;

/**
 * Sets a {@link SentryKafkaProducerInterceptor} on {@link KafkaTemplate} beans via {@link
 * KafkaTemplate#setProducerInterceptor(ProducerInterceptor)}. The original bean is not replaced.
 *
 * <p>If the template already has a {@link ProducerInterceptor}, both are composed using {@link
 * CompositeProducerInterceptor}. Reading the existing interceptor requires reflection (no public
 * getter in Spring Kafka 3.x); if reflection fails, a warning is logged and only the Sentry
 * interceptor is set.
 */
@ApiStatus.Internal
public final class SentryKafkaProducerBeanPostProcessor
    implements BeanPostProcessor, PriorityOrdered {

  @Override
  @SuppressWarnings("unchecked")
  public @NotNull Object postProcessAfterInitialization(
      final @NotNull Object bean, final @NotNull String beanName) throws BeansException {
    if (bean instanceof KafkaTemplate) {
      final @NotNull KafkaTemplate<?, ?> template = (KafkaTemplate<?, ?>) bean;
      final @Nullable ProducerInterceptor<?, ?> existing = getExistingInterceptor(template);

      if (existing instanceof SentryKafkaProducerInterceptor) {
        return bean;
      }

      @SuppressWarnings("rawtypes")
      final SentryKafkaProducerInterceptor sentryInterceptor =
          new SentryKafkaProducerInterceptor<>(
              ScopesAdapter.getInstance(), "auto.queue.spring_jakarta.kafka.producer");

      if (existing != null) {
        @SuppressWarnings("rawtypes")
        final CompositeProducerInterceptor composite =
            new CompositeProducerInterceptor(sentryInterceptor, existing);
        template.setProducerInterceptor(composite);
      } else {
        template.setProducerInterceptor(sentryInterceptor);
      }
    }
    return bean;
  }

  @SuppressWarnings("unchecked")
  private @Nullable ProducerInterceptor<?, ?> getExistingInterceptor(
      final @NotNull KafkaTemplate<?, ?> template) {
    try {
      final @NotNull Field field = KafkaTemplate.class.getDeclaredField("producerInterceptor");
      field.setAccessible(true);
      return (ProducerInterceptor<?, ?>) field.get(template);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      ScopesAdapter.getInstance()
          .getOptions()
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Unable to read existing producerInterceptor from KafkaTemplate via reflection. "
                  + "If you had a custom ProducerInterceptor, it may be overwritten by Sentry's interceptor.",
              e);
      return null;
    }
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }
}
