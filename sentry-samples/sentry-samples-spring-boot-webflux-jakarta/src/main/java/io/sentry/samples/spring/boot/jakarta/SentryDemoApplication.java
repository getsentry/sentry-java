package io.sentry.samples.spring.boot.jakarta;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootApplication
// @EnableMongoRepositories(basePackages = "io.sentry.samples")
@EnableReactiveMongoRepositories
public class SentryDemoApplication {
  public static void main(String[] args) {
    SpringApplication.run(SentryDemoApplication.class, args);
  }

  @Bean
  WebClient webClient(WebClient.Builder builder) {
    return builder.build();
  }
}
