package io.sentry.spring.jakarta.kafka;

import io.sentry.ScopesAdapter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.kafka.core.KafkaTemplate;

/** Wraps {@link KafkaTemplate} beans in {@link SentryKafkaProducerWrapper} for instrumentation. */
@ApiStatus.Internal
public final class SentryKafkaProducerBeanPostProcessor
    implements BeanPostProcessor, PriorityOrdered {

  @Override
  @SuppressWarnings("unchecked")
  public @NotNull Object postProcessAfterInitialization(
      final @NotNull Object bean, final @NotNull String beanName) throws BeansException {
    if (bean instanceof KafkaTemplate && !(bean instanceof SentryKafkaProducerWrapper)) {
      return new SentryKafkaProducerWrapper<>(
          (KafkaTemplate<?, ?>) bean, ScopesAdapter.getInstance());
    }
    return bean;
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }
}
