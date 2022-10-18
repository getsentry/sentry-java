package io.sentry.spring.boot.jakarta;

import ch.qos.logback.classic.LoggerContext;
import com.jakewharton.nopen.annotation.Open;
import io.sentry.logback.SentryAppender;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Auto-configures {@link SentryAppender}. */
@Configuration(proxyBeanMethods = false)
@Open
@ConditionalOnClass({LoggerContext.class, SentryAppender.class})
@ConditionalOnProperty(name = "sentry.logging.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnBean(SentryProperties.class)
public class SentryLogbackAppenderAutoConfiguration {

  @Bean
  public @NotNull SentryLogbackInitializer sentryLogbackInitializer(
      final @NotNull SentryProperties sentryProperties) {
    return new SentryLogbackInitializer(sentryProperties);
  }
}
