package io.sentry.spring;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.IHub;
import io.sentry.SentryOptions;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;

/** Registers Spring Web specific Sentry beans. */
@Configuration
@Open
public class SentryWebConfiguration {

  @Bean
  @Lazy
  @Order(0)
  public @NotNull HttpServletRequestSentryUserProvider httpServletRequestSentryUserProvider(
      final @NotNull SentryOptions sentryOptions) {
    return new HttpServletRequestSentryUserProvider(sentryOptions);
  }

  @Bean
  public @NotNull SentrySpringRequestListener sentrySpringRequestListener(
      final @NotNull IHub sentryHub, final @NotNull SentryOptions sentryOptions) {
    return new SentrySpringRequestListener(sentryHub, sentryOptions);
  }

  @Bean
  public @NotNull SentryExceptionResolver sentryExceptionResolver(final @NotNull IHub sentryHub,
      @Value("${sentry.exceptionResolverOrder:-2147483648}") final @NotNull Integer order) {
    return new SentryExceptionResolver(sentryHub, order);
  }
}
