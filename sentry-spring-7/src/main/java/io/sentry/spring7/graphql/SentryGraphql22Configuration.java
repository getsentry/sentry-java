package io.sentry.spring7.graphql;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.SentryIntegrationPackageStorage;
import io.sentry.graphql.SentryGraphqlInstrumentation;
import io.sentry.graphql22.SentryInstrumentation;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@Configuration(proxyBeanMethods = false)
@Open
public class SentryGraphql22Configuration {

  @Bean(name = "sentryInstrumentation")
  @ConditionalOnMissingBean(name = "sentryInstrumentation")
  @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
  public SentryInstrumentation sentryInstrumentationWebMvc(
      final @NotNull ObjectProvider<SentryGraphqlInstrumentation.BeforeSpanCallback>
              beforeSpanCallback) {
    SentryIntegrationPackageStorage.getInstance().addIntegration("Spring6GrahQLWebMVC");
    return createInstrumentation(beforeSpanCallback, false);
  }

  @Bean(name = "sentryInstrumentation")
  @ConditionalOnMissingBean(name = "sentryInstrumentation")
  @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
  public SentryInstrumentation sentryInstrumentationWebflux(
      final @NotNull ObjectProvider<SentryGraphqlInstrumentation.BeforeSpanCallback>
              beforeSpanCallback) {
    SentryIntegrationPackageStorage.getInstance().addIntegration("Spring6GrahQLWebFlux");
    return createInstrumentation(beforeSpanCallback, true);
  }

  /**
   * We're not setting defaultDataFetcherExceptionHandler here on purpose and instead use the
   * resolver adapter below. This way Springs handler can still forward to other resolver adapters.
   */
  private SentryInstrumentation createInstrumentation(
      final @NotNull ObjectProvider<SentryGraphqlInstrumentation.BeforeSpanCallback>
              beforeSpanCallback,
      final boolean captureRequestBody) {
    return new SentryInstrumentation(
        beforeSpanCallback.getIfAvailable(),
        new SentrySpringSubscriptionHandler(),
        captureRequestBody);
  }

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  public SentryDataFetcherExceptionResolverAdapter exceptionResolverAdapter() {
    return new SentryDataFetcherExceptionResolverAdapter();
  }

  @Bean
  public SentryGraphqlBeanPostProcessor graphqlBeanPostProcessor() {
    return new SentryGraphqlBeanPostProcessor();
  }
}
