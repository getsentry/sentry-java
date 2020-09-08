package io.sentry.spring;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.core.Sentry;
import io.sentry.core.SentryOptions;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

/** Initializes Sentry after all beans are registered. */
@Open
public class SentryInitBeanPostProcessor implements BeanPostProcessor {
  @Override
  public Object postProcessAfterInitialization(
      final @NotNull Object bean, @NotNull final String beanName) throws BeansException {
    if (bean instanceof SentryOptions) {
      Sentry.init((SentryOptions) bean);
    }
    return bean;
  }
}
