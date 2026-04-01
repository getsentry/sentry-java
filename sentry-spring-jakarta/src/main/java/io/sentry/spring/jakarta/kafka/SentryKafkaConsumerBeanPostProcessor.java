package io.sentry.spring.jakarta.kafka;

import io.sentry.ScopesAdapter;
import java.lang.reflect.Field;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.kafka.config.AbstractKafkaListenerContainerFactory;
import org.springframework.kafka.listener.RecordInterceptor;

/**
 * Registers {@link SentryKafkaRecordInterceptor} on {@link AbstractKafkaListenerContainerFactory}
 * beans. If an existing {@link RecordInterceptor} is already set, it is composed as a delegate.
 */
@ApiStatus.Internal
public final class SentryKafkaConsumerBeanPostProcessor
    implements BeanPostProcessor, PriorityOrdered {

  @Override
  @SuppressWarnings("unchecked")
  public @NotNull Object postProcessAfterInitialization(
      final @NotNull Object bean, final @NotNull String beanName) throws BeansException {
    if (bean instanceof AbstractKafkaListenerContainerFactory) {
      final @NotNull AbstractKafkaListenerContainerFactory<?, ?, ?> factory =
          (AbstractKafkaListenerContainerFactory<?, ?, ?>) bean;

      final @Nullable RecordInterceptor<?, ?> existing = getExistingInterceptor(factory);
      if (existing instanceof SentryKafkaRecordInterceptor) {
        return bean;
      }

      @SuppressWarnings("rawtypes")
      final RecordInterceptor sentryInterceptor =
          new SentryKafkaRecordInterceptor<>(ScopesAdapter.getInstance(), existing);
      factory.setRecordInterceptor(sentryInterceptor);
    }
    return bean;
  }

  @SuppressWarnings("unchecked")
  private @Nullable RecordInterceptor<?, ?> getExistingInterceptor(
      final @NotNull AbstractKafkaListenerContainerFactory<?, ?, ?> factory) {
    try {
      final @NotNull Field field =
          AbstractKafkaListenerContainerFactory.class.getDeclaredField("recordInterceptor");
      field.setAccessible(true);
      return (RecordInterceptor<?, ?>) field.get(factory);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      return null;
    }
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }
}
