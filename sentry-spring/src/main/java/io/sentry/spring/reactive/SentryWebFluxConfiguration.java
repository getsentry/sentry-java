package io.sentry.spring.reactive;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.IHub;
import io.sentry.SentryOptions;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;

/** Registers Spring Web Flux specific Sentry beans. */
@Configuration
@Open
public class SentryWebFluxConfiguration {

  @Bean
  @Lazy
  @Order(0)
  public @NotNull WebfluxRequestSentryUserProvider webfluxRequestSentryUserProvider(
      final @NotNull SentryOptions sentryOptions) {
    return new WebfluxRequestSentryUserProvider(sentryOptions);
  }

  @Bean
  public @NotNull SentryReactiveWebFilter sentryReactiveWebFilter(
      final @NotNull IHub hub,
      final @NotNull SentryOptions options,
      final @NotNull List<SentryReactiveUserProvider> userProviders) {
    return new SentryReactiveWebFilter(hub, options, userProviders);
  }

  @Bean
  public @NotNull SentryReactiveExceptionHandler sentryReactiveErrorAttributes() {
    return new SentryReactiveExceptionHandler();
  }
}
