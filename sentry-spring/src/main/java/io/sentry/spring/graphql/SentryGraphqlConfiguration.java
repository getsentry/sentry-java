package io.sentry.spring.graphql;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.graphql.SentryInstrumentation;
import org.springframework.boot.autoconfigure.graphql.GraphQlSourceBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@Configuration(proxyBeanMethods = false)
@Open
public class SentryGraphqlConfiguration {

  /**
   * We're not setting defaultDataFetcherExceptionHandler here on purpose and instead use the
   * resolver adapter below. This way Springs handler can still forward to other resolver adapters.
   */
  @Bean
  public GraphQlSourceBuilderCustomizer sourceBuilderCustomizer() {
    return (builder) ->
        builder.configureGraphQl(
            graphQlBuilder ->
                graphQlBuilder.instrumentation(
                    new SentryInstrumentation(null, new SentrySpringSubscriptionHandler(), true)));
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
