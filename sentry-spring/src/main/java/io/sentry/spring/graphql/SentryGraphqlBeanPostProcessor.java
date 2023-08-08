package io.sentry.spring.graphql;

import org.jetbrains.annotations.ApiStatus;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.graphql.execution.BatchLoaderRegistry;

@ApiStatus.Internal
public final class SentryGraphqlBeanPostProcessor implements BeanPostProcessor {
  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
    if (bean instanceof BatchLoaderRegistry) {
      return new SentryBatchLoaderRegistry((BatchLoaderRegistry) bean);
    }
    return bean;
  }
}
