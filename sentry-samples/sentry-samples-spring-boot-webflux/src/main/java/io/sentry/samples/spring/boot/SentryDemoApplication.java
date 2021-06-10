package io.sentry.samples.spring.boot;

import io.sentry.IHub;
import io.sentry.reactor.SentryScheduleHook;
import io.sentry.reactor.SentryWebExceptionHandler;
import io.sentry.reactor.SentryWebFilter;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import reactor.core.scheduler.Schedulers;

@SpringBootApplication
public class SentryDemoApplication {
  public static void main(String[] args) {
    SpringApplication.run(SentryDemoApplication.class, args);
  }

  @Bean
  ApplicationRunner applicationRunner() {
    return args -> {
      Schedulers.onScheduleHook("sentry", new SentryScheduleHook());
    };
  }

  @Bean
  SentryWebFilter sentryWebFilter(final IHub hub) {
    return new SentryWebFilter(hub);
  }

  @Bean
  SentryWebExceptionHandler sentryWebExceptionHandler(final IHub hub) {
    return new SentryWebExceptionHandler(hub);
  }
}
