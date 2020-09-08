package io.sentry.spring;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.core.IHub;
import io.sentry.core.SentryOptions;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers Spring Web specific Sentry beans. */
@Configuration
@Open
public class SentryWebConfiguration {

  @Bean
  public @NotNull SentryRequestFilter sentryRequestFilter(
      final @NotNull IHub sentryHub, final @NotNull SentryOptions sentryOptions) {
    return new SentryRequestFilter(sentryHub, sentryOptions);
  }

  @Bean
  public @NotNull SentryExceptionResolver sentryExceptionResolver(final @NotNull IHub sentryHub) {
    return new SentryExceptionResolver(sentryHub);
  }
}
