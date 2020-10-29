package io.sentry.spring;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.IHub;
import io.sentry.SentryOptions;
import org.jetbrains.annotations.NotNull;
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
  public @NotNull SentryRequestResolver sentryRequestResolver(
      final @NotNull SentryOptions options) {
    return new SentryRequestResolver(options);
  }

  @Bean
  public @NotNull SentrySpringRequestListener sentrySpringRequestListener(
      final @NotNull IHub sentryHub, final @NotNull SentryRequestResolver requestResolver) {
    return new SentrySpringRequestListener(sentryHub, requestResolver);
  }

  @Bean
  public @NotNull SentryExceptionResolver sentryExceptionResolver(final @NotNull IHub sentryHub) {
    return new SentryExceptionResolver(sentryHub);
  }
}
