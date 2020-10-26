package io.sentry.samples.spring;

import io.sentry.spring.EnableSentry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
// NOTE: Replace the test DSN below with YOUR OWN DSN to see the events from this app in your Sentry
// project/dashboard
@EnableSentry(
    dsn = "https://2a453604446b4630a61e0ebbcf087e95@o458011.ingest.sentry.io/5467837",
    sendDefaultPii = true)
public class SentryDemoApplication {
  public static void main(String[] args) {
    SpringApplication.run(SentryDemoApplication.class, args);
  }
}
