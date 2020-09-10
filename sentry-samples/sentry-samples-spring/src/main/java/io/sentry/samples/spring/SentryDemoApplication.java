package io.sentry.samples.spring;

import io.sentry.spring.EnableSentry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
// NOTE: Replace the test DSN below with YOUR OWN DSN to see the events from this app in your Sentry
// project/dashboard
@EnableSentry(
    dsn = "https://f7f320d5c3a54709be7b28e0f2ca7081@sentry.io/1808954",
    sendDefaultPii = true)
public class SentryDemoApplication {
  public static void main(String[] args) {
    SpringApplication.run(SentryDemoApplication.class, args);
  }
}
