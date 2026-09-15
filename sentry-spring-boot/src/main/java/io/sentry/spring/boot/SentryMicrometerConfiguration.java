package io.sentry.spring.boot;

import io.sentry.IScopes;
import io.sentry.micrometer.SentryMeterRegistry;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configures the Sentry Micrometer registry. */
@Configuration(proxyBeanMethods = false)
final class SentryMicrometerConfiguration {

  @Bean(destroyMethod = "close")
  @ConditionalOnMissingBean(SentryMeterRegistry.class)
  public @NotNull SentryMeterRegistry sentryMeterRegistry(
      final @NotNull IScopes scopes, final @NotNull SentryProperties properties) {
    return new SentryMeterRegistry(properties.getMicrometer().getPollIntervalMillis());
  }
}
