package io.sentry.spring.boot4;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import com.jakewharton.nopen.annotation.Open;
import io.sentry.logback.SentryAppender;
import io.sentry.util.Objects;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.GenericApplicationListener;
import org.springframework.core.ResolvableType;

/** Registers {@link SentryAppender} after Spring context gets refreshed. */
@Open
public class SentryLogbackInitializer implements GenericApplicationListener {
  private final @NotNull SentryProperties sentryProperties;
  private final @NotNull List<String> loggers;
  @Nullable private SentryAppender sentryAppender;

  public SentryLogbackInitializer(final @NotNull SentryProperties sentryProperties) {
    this.sentryProperties = Objects.requireNonNull(sentryProperties, "properties are required");
    loggers = sentryProperties.getLogging().getLoggers();
  }

  @Override
  public boolean supportsEventType(final @NotNull ResolvableType eventType) {
    return eventType.getRawClass() != null
        && ContextRefreshedEvent.class.isAssignableFrom(eventType.getRawClass());
  }

  @Override
  public void onApplicationEvent(final @NotNull ApplicationEvent event) {
    this.loggers.forEach(
        loggerName -> {
          final Logger logger = (Logger) LoggerFactory.getLogger(loggerName);
          if (!isSentryAppenderRegistered(logger)) {
            final SentryAppender sentryAppender = getSentryAppender();

            Optional.ofNullable(sentryProperties.getLogging().getMinimumBreadcrumbLevel())
                .map(slf4jLevel -> Level.toLevel(slf4jLevel.name()))
                .ifPresent(sentryAppender::setMinimumBreadcrumbLevel);
            Optional.ofNullable(sentryProperties.getLogging().getMinimumEventLevel())
                .map(slf4jLevel -> Level.toLevel(slf4jLevel.name()))
                .ifPresent(sentryAppender::setMinimumEventLevel);
            Optional.ofNullable(sentryProperties.getLogging().getMinimumLevel())
                .map(slf4jLevel -> Level.toLevel(slf4jLevel.name()))
                .ifPresent(sentryAppender::setMinimumLevel);

            sentryAppender.start();
            logger.addAppender(sentryAppender);
          }
        });
  }

  @NotNull
  private SentryAppender getSentryAppender() {
    if (sentryAppender == null) {
      sentryAppender = new SentryAppender();
      sentryAppender.setName("SENTRY_APPENDER");
      sentryAppender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
    }
    return sentryAppender;
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
