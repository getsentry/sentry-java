package io.sentry.samples.netflix.dgs;

import com.netflix.graphql.dgs.exceptions.DefaultDataFetcherExceptionHandler;
import io.sentry.graphql.java.SentryDataFetcherExceptionHandler;
import io.sentry.graphql.java.SentryInstrumentation;
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
    return new SentryInstrumentation();
  }

  @Bean
  SentryDataFetcherExceptionHandler sentryDataFetcherExceptionHandler() {
    // delegate to default Netflix DGS exception handler
    return new SentryDataFetcherExceptionHandler(new DefaultDataFetcherExceptionHandler());
  }
}
