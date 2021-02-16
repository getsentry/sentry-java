package io.sentry.spring;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.EventProcessor;
import io.sentry.ITransportFactory;
import io.sentry.Sentry;
import io.sentry.SentryOptions;
import io.sentry.SentryOptions.TracesSamplerCallback;
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
  @SuppressWarnings("unchecked")
  public Object postProcessAfterInitialization(
      final @NotNull Object bean, @NotNull final String beanName) throws BeansException {
    if (bean instanceof SentryOptions) {
      final SentryOptions options = (SentryOptions) bean;

      if (applicationContext != null) {
        applicationContext
            .getBeanProvider(SentryUserProvider.class)
            .orderedStream()
            .forEach(
                sentryUserProvider ->
                    options.addEventProcessor(
                        new SentryUserProviderEventProcessor(options, sentryUserProvider)));
        applicationContext
            .getBeanProvider(TracesSamplerCallback.class)
            .ifAvailable(options::setTracesSampler);
        applicationContext
            .getBeanProvider(ITransportFactory.class)
            .ifAvailable(options::setTransportFactory);
        applicationContext
            .getBeanProvider(SentryOptions.BeforeSendCallback.class)
            .ifAvailable(options::setBeforeSend);
        applicationContext
            .getBeanProvider(SentryOptions.BeforeBreadcrumbCallback.class)
            .ifAvailable(options::setBeforeBreadcrumb);
        applicationContext
            .getBeanProvider(Sentry.OptionsConfiguration.class)
            .ifAvailable(optionsConfiguration -> optionsConfiguration.configure(options));
        applicationContext
            .getBeansOfType(EventProcessor.class)
            .values()
            .forEach(options::addEventProcessor);
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
