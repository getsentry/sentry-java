package io.sentry.samples.netflix.dgs;

import com.netflix.graphql.dgs.exceptions.DefaultDataFetcherExceptionHandler;
import io.sentry.graphql.SentryGenericDataFetcherExceptionHandler;
import io.sentry.graphql.SentryInstrumentation;
import io.sentry.spring.graphql.SentryDgsSubscriptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class NetlixDgsApplication {

  public static void main(String[] args) {
    SpringApplication.run(NetlixDgsApplication.class, args);
  }

  @Bean
  SentryInstrumentation sentryInstrumentation() {
    return new SentryInstrumentation(new SentryDgsSubscriptionHandler(), true);
  }

  @Bean
  SentryGenericDataFetcherExceptionHandler sentryDataFetcherExceptionHandler() {
    // delegate to default Netflix DGS exception handler
    return new SentryGenericDataFetcherExceptionHandler(new DefaultDataFetcherExceptionHandler());
  }
}
