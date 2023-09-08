package io.sentry.spring.graphql;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.SentryIntegrationPackageStorage;
import io.sentry.graphql.SentryInstrumentation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.graphql.GraphQlSourceBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@Configuration(proxyBeanMethods = false)
@Open
public class SentryGraphqlConfiguration {

  @Bean
  @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
  public GraphQlSourceBuilderCustomizer sourceBuilderCustomizerWebmvc() {
    SentryIntegrationPackageStorage.getInstance().addIntegration("Spring5GrahQLWebMVC");
    return sourceBuilderCustomizer(false);
  }

  @Bean
  @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
  public GraphQlSourceBuilderCustomizer sourceBuilderCustomizerWebflux() {
    SentryIntegrationPackageStorage.getInstance().addIntegration("Spring5GrahQLWebFlux");
    return sourceBuilderCustomizer(true);
  }

  /**
   * We're not setting defaultDataFetcherExceptionHandler here on purpose and instead use the
   * resolver adapter below. This way Springs handler can still forward to other resolver adapters.
   */
  private GraphQlSourceBuilderCustomizer sourceBuilderCustomizer(final boolean captureRequestBody) {
    return (builder) ->
        builder.configureGraphQl(
            graphQlBuilder ->
                graphQlBuilder.instrumentation(
                    new SentryInstrumentation(
                        null, new SentrySpringSubscriptionHandler(), captureRequestBody)));
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
