package io.sentry.spring.jakarta;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.SentryOptions;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;

/** Registers Spring Web specific Sentry beans. */
@Configuration(proxyBeanMethods = false)
@Open
public class SentryWebConfiguration {

  @Bean
  @Lazy
  @Order(0)
  public @NotNull HttpServletRequestSentryUserProvider httpServletRequestSentryUserProvider(
      final @NotNull SentryOptions sentryOptions) {
    return new HttpServletRequestSentryUserProvider(sentryOptions);
  }
}
