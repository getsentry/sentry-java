package io.sentry.spring.boot4;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.log4j2.SentryAppender;
import org.apache.logging.log4j.core.LoggerContext;
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
public class SentryLog4j2AppenderAutoConfiguration {

  @Bean
  public @NotNull SentryLog4j2Initializer sentryLog4j2Initializer(
      final @NotNull SentryProperties sentryProperties) {
    return new SentryLog4j2Initializer(sentryProperties);
  }
}
