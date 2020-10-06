package io.sentry.spring.boot;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import com.jakewharton.nopen.annotation.Open;
import io.sentry.logback.SentryAppender;
import java.util.Iterator;
import java.util.Optional;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/** Auto-configures {@link SentryAppender}. */
@Configuration
@Open
@ConditionalOnClass({LoggerContext.class, SentryAppender.class})
@ConditionalOnProperty(name = "sentry.logging.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnBean(SentryProperties.class)
public class SentryLogbackAppenderAutoConfiguration implements InitializingBean {

  @Autowired private SentryProperties sentryProperties;

  @Override
  public void afterPropertiesSet() {
    Logger rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);

    if (!isSentryAppenderRegistered(rootLogger)) {
      SentryAppender sentryAppender = new SentryAppender();
      sentryAppender.setName("SENTRY_APPENDER");
      sentryAppender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());

      Optional.ofNullable(sentryProperties.getLogging().getMinimumBreadcrumbLevel())
          .ifPresent(sentryAppender::setMinimumBreadcrumbLevel);
      Optional.ofNullable(sentryProperties.getLogging().getMinimumEventLevel())
          .ifPresent(sentryAppender::setMinimumEventLevel);

      sentryAppender.start();
      rootLogger.addAppender(sentryAppender);
    }
  }

  private boolean isSentryAppenderRegistered(Logger logger) {
    Iterator<Appender<ILoggingEvent>> it = logger.iteratorForAppenders();
    while (it.hasNext()) {
      Appender<ILoggingEvent> appender = it.next();

      if (appender.getClass().equals(SentryAppender.class)) {
        return true;
      }
    }
    return false;
  }
}
