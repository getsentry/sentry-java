package io.sentry.spring.boot4;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.ScopesAdapter;
import io.sentry.log4j2.SentryAppender;
import io.sentry.util.Objects;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.GenericApplicationListener;
import org.springframework.core.ResolvableType;

/** Registers {@link SentryAppender} after Spring context gets refreshed. */
@Open
public class SentryLog4j2Initializer implements GenericApplicationListener {
  private static final Logger logger = LoggerFactory.getLogger(SentryLog4j2Initializer.class);
  private static final String SENTRY_APPENDER_NAME = "SENTRY_APPENDER";

  private final @NotNull SentryProperties sentryProperties;
  private final @NotNull List<String> loggers;
  @Nullable private SentryAppender sentryAppender;

  public SentryLog4j2Initializer(final @NotNull SentryProperties sentryProperties) {
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
    final Object context = LogManager.getContext(false);
    if (!(context instanceof LoggerContext)) {
      logger.info(
          "Sentry Log4j2 appender was not configured because Log4j2 Core is not the active logging backend. Log4j2 API calls may be routed through SLF4J.");
      return;
    }

    final LoggerContext loggerContext = (LoggerContext) context;
    final Configuration configuration = loggerContext.getConfiguration();

    final Set<String> loggerNames = normalizeLoggerNames(loggers);
    boolean changed = false;
    for (final String loggerName : loggerNames) {
      if (isCoveredByAncestorLogger(configuration, loggerName, loggerNames)
          || hasSentryAppenderRegisteredOnAncestor(configuration, loggerName)) {
        continue;
      }

      final LoggerConfig loggerConfig = getOrCreateLoggerConfig(configuration, loggerName);
      if (!isSentryAppenderRegistered(loggerConfig)) {
        final SentryAppender sentryAppender = getSentryAppender(configuration);
        loggerConfig.addAppender(sentryAppender, null, null);
        changed = true;
      }
    }

    if (changed) {
      loggerContext.updateLoggers(configuration);
    }
  }

  private @NotNull LoggerConfig getOrCreateLoggerConfig(
      final @NotNull Configuration configuration, final @NotNull String loggerName) {
    if (LogManager.ROOT_LOGGER_NAME.equals(loggerName)) {
      return configuration.getRootLogger();
    }

    final LoggerConfig loggerConfig = configuration.getLoggerConfig(loggerName);
    if (loggerName.equals(loggerConfig.getName())) {
      return loggerConfig;
    }

    final LoggerConfig newLoggerConfig = new LoggerConfig(loggerName, null, true);
    newLoggerConfig.setParent(loggerConfig);
    configuration.addLogger(loggerName, newLoggerConfig);
    return newLoggerConfig;
  }

  private @NotNull SentryAppender getSentryAppender(final @NotNull Configuration configuration) {
    if (sentryAppender == null) {
      sentryAppender =
          new SentryAppender(
              SENTRY_APPENDER_NAME,
              null,
              null,
              toLog4jLevel(sentryProperties.getLogging().getMinimumBreadcrumbLevel()),
              toLog4jLevel(sentryProperties.getLogging().getMinimumEventLevel()),
              toLog4jLevel(sentryProperties.getLogging().getMinimumLevel()),
              null,
              null,
              ScopesAdapter.getInstance(),
              null);
      sentryAppender.start();
      configuration.addAppender(sentryAppender);
    }
    return sentryAppender;
  }

  private @NotNull Set<String> normalizeLoggerNames(final @NotNull List<String> loggerNames) {
    final Set<String> normalizedLoggerNames = new LinkedHashSet<>();
    for (final String loggerName : loggerNames) {
      if (loggerName == null || loggerName.trim().isEmpty()) {
        continue;
      }
      normalizedLoggerNames.add(normalizeLoggerName(loggerName.trim()));
    }
    return normalizedLoggerNames;
  }

  private boolean isCoveredByAncestorLogger(
      final @NotNull Configuration configuration,
      final @NotNull String loggerName,
      final @NotNull Set<String> loggerNames) {
    return loggerNames.stream()
        .anyMatch(
            candidate ->
                isAncestorLogger(candidate, loggerName)
                    && isAdditivePathToAncestor(configuration, loggerName, candidate));
  }

  private boolean hasSentryAppenderRegisteredOnAncestor(
      final @NotNull Configuration configuration, final @NotNull String loggerName) {
    if (LogManager.ROOT_LOGGER_NAME.equals(loggerName)) {
      return false;
    }

    @Nullable String parentLoggerName = getParentLoggerName(loggerName);
    while (parentLoggerName != null) {
      final LoggerConfig parentLoggerConfig = configuration.getLoggerConfig(parentLoggerName);
      if (parentLoggerName.equals(parentLoggerConfig.getName())
          && isSentryAppenderRegistered(parentLoggerConfig)
          && isAdditivePathToAncestor(configuration, loggerName, parentLoggerName)) {
        return true;
      }
      parentLoggerName = getParentLoggerName(parentLoggerName);
    }

    return isSentryAppenderRegistered(configuration.getRootLogger())
        && isAdditivePathToAncestor(configuration, loggerName, LogManager.ROOT_LOGGER_NAME);
  }

  private boolean isAdditivePathToAncestor(
      final @NotNull Configuration configuration,
      final @NotNull String loggerName,
      final @NotNull String ancestorLoggerName) {
    String currentLoggerName = loggerName;
    while (!ancestorLoggerName.equals(currentLoggerName)) {
      final LoggerConfig loggerConfig = configuration.getLoggerConfig(currentLoggerName);
      if (currentLoggerName.equals(loggerConfig.getName()) && !loggerConfig.isAdditive()) {
        return false;
      }
      currentLoggerName = getParentLoggerName(currentLoggerName);
      if (currentLoggerName == null) {
        currentLoggerName = LogManager.ROOT_LOGGER_NAME;
      }
    }
    return true;
  }

  private @Nullable String getParentLoggerName(final @NotNull String loggerName) {
    final int separator = loggerName.lastIndexOf('.');
    return separator > -1 ? loggerName.substring(0, separator) : null;
  }

  private boolean isAncestorLogger(
      final @NotNull String candidateLoggerName, final @NotNull String loggerName) {
    if (candidateLoggerName.equals(loggerName)) {
      return false;
    }
    return LogManager.ROOT_LOGGER_NAME.equals(candidateLoggerName)
        || loggerName.startsWith(candidateLoggerName + ".");
  }

  private boolean isSentryAppenderRegistered(final @NotNull LoggerConfig loggerConfig) {
    return loggerConfig.getAppenders().values().stream()
        .anyMatch(appender -> appender.getClass().equals(SentryAppender.class));
  }

  private @NotNull String normalizeLoggerName(final @NotNull String loggerName) {
    if (org.slf4j.Logger.ROOT_LOGGER_NAME.equals(loggerName)) {
      return LogManager.ROOT_LOGGER_NAME;
    }
    return loggerName;
  }

  private @Nullable Level toLog4jLevel(final @Nullable org.slf4j.event.Level slf4jLevel) {
    return slf4jLevel == null ? null : Level.getLevel(slf4jLevel.name());
  }
}
