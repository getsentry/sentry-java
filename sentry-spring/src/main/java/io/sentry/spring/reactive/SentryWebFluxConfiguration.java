package io.sentry.spring.reactive;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.IHub;
import io.sentry.SentryOptions;
import io.sentry.spring.HttpServletRequestSentryUserProvider;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;

/** Registers Spring Web specific Sentry beans. */
// @Configuration
@Open
public class SentryWebFluxConfiguration {

  @Bean
  @Lazy
  @Order(0)
  public @NotNull HttpServletRequestSentryUserProvider httpServletRequestSentryUserProvider(
      final @NotNull SentryOptions sentryOptions) {
    return new HttpServletRequestSentryUserProvider(sentryOptions);
  }

  @Bean
  public @NotNull SentryReactiveWebFilter sentryReactiveWebFilter(
      final @NotNull IHub sentryHub, final @NotNull SentryOptions sentryOptions) {
    return new SentryReactiveWebFilter(sentryHub, sentryOptions);
  }

  @Bean
  public @NotNull SentryReactiveErrorAttributes sentryReactiveErrorAttributes() {
    return new SentryReactiveErrorAttributes();
  }
}
