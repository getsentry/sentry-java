package io.sentry.spring;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.IHub;
import io.sentry.SentryOptions;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/** Registers Spring Web specific Sentry beans. */
@Configuration
@Open
public class SentryWebConfiguration {

  @Bean
  @Lazy
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
  public @NotNull SentryExceptionResolver sentryExceptionResolver(final @NotNull IHub sentryHub) {
    return new SentryExceptionResolver(sentryHub);
  }
}
