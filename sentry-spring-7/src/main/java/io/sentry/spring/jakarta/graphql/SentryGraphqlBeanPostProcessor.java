package io.sentry.spring.jakarta.graphql;

import org.jetbrains.annotations.ApiStatus;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.graphql.execution.BatchLoaderRegistry;

@ApiStatus.Internal
public final class SentryGraphqlBeanPostProcessor implements BeanPostProcessor, PriorityOrdered {

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
    if (bean instanceof BatchLoaderRegistry) {
      return new SentryBatchLoaderRegistry((BatchLoaderRegistry) bean);
    }
    return bean;
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }
}
