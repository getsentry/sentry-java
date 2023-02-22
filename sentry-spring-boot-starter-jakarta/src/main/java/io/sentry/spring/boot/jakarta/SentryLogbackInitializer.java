package io.sentry.spring.boot.jakarta;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import com.jakewharton.nopen.annotation.Open;
import io.sentry.logback.SentryAppender;
import io.sentry.util.Objects;
import java.util.Iterator;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.GenericApplicationListener;
import org.springframework.core.ResolvableType;

/** Registers {@link SentryAppender} after Spring context gets refreshed. */
@Open
class SentryLogbackInitializer implements GenericApplicationListener {
  private final @NotNull SentryProperties sentryProperties;

  public SentryLogbackInitializer(final @NotNull SentryProperties sentryProperties) {
    this.sentryProperties = Objects.requireNonNull(sentryProperties, "properties are required");
  }

  @Override
  public boolean supportsEventType(final @NotNull ResolvableType eventType) {
    return eventType.getRawClass() != null
        && ContextRefreshedEvent.class.isAssignableFrom(eventType.getRawClass());
  }

  @Override
  public void onApplicationEvent(final @NotNull ApplicationEvent event) {
    final Logger rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);

    if (!isSentryAppenderRegistered(rootLogger)) {
      final SentryAppender sentryAppender = new SentryAppender();
      sentryAppender.setName("SENTRY_APPENDER");
      sentryAppender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());

      Optional.ofNullable(sentryProperties.getLogging().getMinimumBreadcrumbLevel())
          .map(slf4jLevel -> Level.toLevel(slf4jLevel.name()))
          .ifPresent(sentryAppender::setMinimumBreadcrumbLevel);
      Optional.ofNullable(sentryProperties.getLogging().getMinimumEventLevel())
          .map(slf4jLevel -> Level.toLevel(slf4jLevel.name()))
          .ifPresent(sentryAppender::setMinimumEventLevel);

      sentryAppender.start();
      rootLogger.addAppender(sentryAppender);
    }
  }

  private boolean isSentryAppenderRegistered(final @NotNull Logger logger) {
    final Iterator<Appender<ILoggingEvent>> it = logger.iteratorForAppenders();
    while (it.hasNext()) {
      final Appender<ILoggingEvent> appender = it.next();

      if (appender.getClass().equals(SentryAppender.class)) {
        return true;
      }
    }
    return false;
  }
}
