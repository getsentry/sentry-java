package io.sentry.spring;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.Sentry;
import io.sentry.SentryOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/** Initializes Sentry after all beans are registered. */
@Open
public class SentryInitBeanPostProcessor implements BeanPostProcessor, ApplicationContextAware {
  private @Nullable ApplicationContext applicationContext;

  @Override
  public Object postProcessAfterInitialization(
      final @NotNull Object bean, @NotNull final String beanName) throws BeansException {
    if (bean instanceof SentryOptions) {
      final SentryOptions options = (SentryOptions) bean;

      if (applicationContext != null) {
        applicationContext
            .getBeanProvider(SentryUserProvider.class)
            .forEach(
                sentryUserProvider ->
                    options.addEventProcessor(
                        new SentryUserProviderEventProcessor(sentryUserProvider)));
      }
      Sentry.init(options);
    }
    return bean;
  }

  @Override
  public void setApplicationContext(final @NotNull ApplicationContext applicationContext)
      throws BeansException {
    this.applicationContext = applicationContext;
  }
}
