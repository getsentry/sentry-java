package io.sentry.spring7;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.EventProcessor;
import io.sentry.IScopes;
import io.sentry.ITransportFactory;
import io.sentry.Integration;
import io.sentry.ScopesAdapter;
import io.sentry.Sentry;
import io.sentry.SentryOptions;
import io.sentry.SentryOptions.TracesSamplerCallback;
import io.sentry.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Initializes Sentry after all beans are registered. Closes Sentry on Spring application context
 * destroy.
 */
@Open
public class SentryInitBeanPostProcessor
    implements BeanPostProcessor, ApplicationContextAware, DisposableBean {
  private @Nullable ApplicationContext applicationContext;

  private final @NotNull IScopes scopes;

  public SentryInitBeanPostProcessor() {
    this(ScopesAdapter.getInstance());
  }

  SentryInitBeanPostProcessor(final @NotNull IScopes scopes) {
    Objects.requireNonNull(scopes, "Scopes are required");
    this.scopes = scopes;
  }

  @Override
  @SuppressWarnings({"unchecked", "deprecation"})
  public @NotNull Object postProcessAfterInitialization(
      final @NotNull Object bean, @NotNull final String beanName) throws BeansException {
    if (bean instanceof SentryOptions) {
      final SentryOptions options = (SentryOptions) bean;

      if (applicationContext != null) {
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
            .getBeanProvider(SentryOptions.BeforeSendTransactionCallback.class)
            .ifAvailable(options::setBeforeSendTransaction);
        applicationContext
            .getBeanProvider(SentryOptions.BeforeBreadcrumbCallback.class)
            .ifAvailable(options::setBeforeBreadcrumb);
        applicationContext
            .getBeansOfType(EventProcessor.class)
            .values()
            .forEach(options::addEventProcessor);
        applicationContext
            .getBeansOfType(Integration.class)
            .values()
            .forEach(options::addIntegration);
        applicationContext
            .getBeanProvider(Sentry.OptionsConfiguration.class)
            .ifAvailable(optionsConfiguration -> optionsConfiguration.configure(options));
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

  @Override
  public void destroy() {
    scopes.close();
  }
}
