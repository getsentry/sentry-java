package io.sentry.samples.spring.boot.jakarta;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootApplication
public class SentryDemoApplication {
  public static void main(String[] args) {
    SpringApplication.run(SentryDemoApplication.class, args);
  }

  @Bean
  WebClient webClient(WebClient.Builder builder) {
    return builder.build();
  }
}
