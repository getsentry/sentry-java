package io.sentry.spring.boot.jakarta.graphql;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.SentryIntegrationPackageStorage;
import io.sentry.graphql.SentryInstrumentation;
import io.sentry.spring.boot.jakarta.SentryProperties;
import io.sentry.spring.jakarta.graphql.SentryDataFetcherExceptionResolverAdapter;
import io.sentry.spring.jakarta.graphql.SentryGraphqlBeanPostProcessor;
import io.sentry.spring.jakarta.graphql.SentrySpringSubscriptionHandler;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.graphql.GraphQlSourceBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@Configuration(proxyBeanMethods = false)
@Open
public class SentryGraphqlAutoConfiguration {

  @Bean
  @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
  public GraphQlSourceBuilderCustomizer sourceBuilderCustomizerWebmvc(
      final @NotNull SentryProperties sentryProperties) {
    SentryIntegrationPackageStorage.getInstance().addIntegration("Spring6GrahQLWebMVC");
    return sourceBuilderCustomizer(sentryProperties, false);
  }

  @Bean
  @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
  public GraphQlSourceBuilderCustomizer sourceBuilderCustomizerWebflux(
      final @NotNull SentryProperties sentryProperties) {
    SentryIntegrationPackageStorage.getInstance().addIntegration("Spring6GrahQLWebFlux");
    return sourceBuilderCustomizer(sentryProperties, true);
  }

  /**
   * We're not setting defaultDataFetcherExceptionHandler here on purpose and instead use the
   * resolver adapter below. This way Springs handler can still forward to other resolver adapters.
   */
  private GraphQlSourceBuilderCustomizer sourceBuilderCustomizer(
      final @NotNull SentryProperties sentryProperties, final boolean captureRequestBody) {
    return (builder) ->
        builder.configureGraphQl(
            graphQlBuilder ->
                graphQlBuilder.instrumentation(
                    new SentryInstrumentation(
                        null,
                        new SentrySpringSubscriptionHandler(),
                        captureRequestBody,
                        sentryProperties.getGraphql().getIgnoredErrorTypes())));
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
