package io.sentry.spring.boot.graphql;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.SentryIntegrationPackageStorage;
import io.sentry.graphql.SentryGraphqlInstrumentation;
import io.sentry.graphql.SentryInstrumentation;
import io.sentry.spring.boot.SentryProperties;
import io.sentry.spring.graphql.SentryDataFetcherExceptionResolverAdapter;
import io.sentry.spring.graphql.SentryGraphqlBeanPostProcessor;
import io.sentry.spring.graphql.SentrySpringSubscriptionHandler;
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
public class SentryGraphqlAutoConfiguration {

  @Bean(name = "sentryInstrumentation")
  @ConditionalOnMissingBean
  @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
  public SentryInstrumentation sentryInstrumentationWebMvc(
      final @NotNull SentryProperties sentryProperties,
      final @NotNull ObjectProvider<SentryGraphqlInstrumentation.BeforeSpanCallback>
              beforeSpanCallback) {
    SentryIntegrationPackageStorage.getInstance().addIntegration("Spring5GrahQLWebMVC");
    return createInstrumentation(sentryProperties, beforeSpanCallback, false);
  }

  @Bean(name = "sentryInstrumentation")
  @ConditionalOnMissingBean
  @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
  public SentryInstrumentation sentryInstrumentationWebflux(
      final @NotNull SentryProperties sentryProperties,
      final @NotNull ObjectProvider<SentryGraphqlInstrumentation.BeforeSpanCallback>
              beforeSpanCallback) {
    SentryIntegrationPackageStorage.getInstance().addIntegration("Spring5GrahQLWebFlux");
    return createInstrumentation(sentryProperties, beforeSpanCallback, true);
  }

  /**
   * We're not setting defaultDataFetcherExceptionHandler here on purpose and instead use the
   * resolver adapter below. This way Springs handler can still forward to other resolver adapters.
   */
  private SentryInstrumentation createInstrumentation(
      final @NotNull SentryProperties sentryProperties,
      final @NotNull ObjectProvider<SentryGraphqlInstrumentation.BeforeSpanCallback>
              beforeSpanCallback,
      final boolean captureRequestBody) {
    return new SentryInstrumentation(
        beforeSpanCallback.getIfAvailable(),
        new SentrySpringSubscriptionHandler(),
        captureRequestBody,
        sentryProperties.getGraphql().getIgnoredErrorTypes());
  }

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  public SentryDataFetcherExceptionResolverAdapter exceptionResolverAdapter() {
    return new SentryDataFetcherExceptionResolverAdapter();
  }

  @Bean
  public static SentryGraphqlBeanPostProcessor graphqlBeanPostProcessor() {
    return new SentryGraphqlBeanPostProcessor();
  }
}
