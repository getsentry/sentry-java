package io.sentry.log4j2;

import static io.sentry.TypeCheckHint.LOG4J_LOG_EVENT;
import static io.sentry.TypeCheckHint.SENTRY_SYNTHETIC_EXCEPTION;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.Breadcrumb;
import io.sentry.DateUtils;
import io.sentry.Hint;
import io.sentry.IScopes;
import io.sentry.ITransportFactory;
import io.sentry.InitPriority;
import io.sentry.ScopesAdapter;
import io.sentry.Sentry;
import io.sentry.SentryAttribute;
import io.sentry.SentryAttributes;
import io.sentry.SentryEvent;
import io.sentry.SentryIntegrationPackageStorage;
import io.sentry.SentryLevel;
import io.sentry.SentryLogLevel;
import io.sentry.SentryOptions;
import io.sentry.exception.ExceptionMechanismException;
import io.sentry.logger.SentryLogParameters;
import io.sentry.protocol.Mechanism;
import io.sentry.protocol.Message;
import io.sentry.protocol.SdkVersion;
import io.sentry.util.CollectionUtils;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.impl.ThrowableProxy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Appender for Log4j2 in charge of sending the logged events to a Sentry server. */
@Plugin(name = "Sentry", category = "Core", elementType = "appender", printObject = true)
@Open
public class SentryAppender extends AbstractAppender {
  public static final String MECHANISM_TYPE = "Log4j2SentryAppender";

  private final @Nullable String dsn;
  private final @Nullable ITransportFactory transportFactory;
  private @NotNull Level minimumBreadcrumbLevel = Level.INFO;
  private @NotNull Level minimumEventLevel = Level.ERROR;
  private @NotNull Level minimumLevel = Level.INFO;
  private final @Nullable Boolean debug;
  private final @NotNull IScopes scopes;
  private final @Nullable List<String> contextTags;

  static {
    SentryIntegrationPackageStorage.getInstance()
        .addPackage("maven:io.sentry:sentry-log4j2", BuildConfig.VERSION_NAME);
  }

  /**
   * @deprecated please use the non deprecated constructor instead.
   */
  @Deprecated
  @SuppressWarnings("InlineMeSuggester")
  public SentryAppender(
      final @NotNull String name,
      final @Nullable Filter filter,
      final @Nullable String dsn,
      final @Nullable Level minimumBreadcrumbLevel,
      final @Nullable Level minimumEventLevel,
      final @Nullable Boolean debug,
      final @Nullable ITransportFactory transportFactory,
      final @NotNull IScopes scopes,
      final @Nullable String[] contextTags) {
    this(
        name,
        filter,
        dsn,
        minimumBreadcrumbLevel,
        minimumEventLevel,
        null,
        debug,
        transportFactory,
        scopes,
        contextTags);
  }

  public SentryAppender(
      final @NotNull String name,
      final @Nullable Filter filter,
      final @Nullable String dsn,
      final @Nullable Level minimumBreadcrumbLevel,
      final @Nullable Level minimumEventLevel,
      final @Nullable Level minimumLevel,
      final @Nullable Boolean debug,
      final @Nullable ITransportFactory transportFactory,
      final @NotNull IScopes scopes,
      final @Nullable String[] contextTags) {
    super(name, filter, null, true, null);
    this.dsn = dsn;
    if (minimumBreadcrumbLevel != null) {
      this.minimumBreadcrumbLevel = minimumBreadcrumbLevel;
    }
    if (minimumEventLevel != null) {
      this.minimumEventLevel = minimumEventLevel;
    }
    if (minimumLevel != null) {
      this.minimumLevel = minimumLevel;
    }
    this.debug = debug;
    this.transportFactory = transportFactory;
    this.scopes = scopes;
    this.contextTags = contextTags != null ? Arrays.asList(contextTags) : null;
  }

  /**
   * Create a Sentry Appender.
   *
   * @param name The name of the Appender.
   * @param minimumBreadcrumbLevel The min. level of the breadcrumb.
   * @param minimumEventLevel The min. level of the event.
   * @param minimumLevel The min. level of the log event.
   * @param dsn the Sentry DSN.
   * @param debug if Sentry debug mode should be on
   * @param filter The filter, if any, to use.
   * @return The SentryAppender.
   */
  @PluginFactory
  public static @Nullable SentryAppender createAppender(
      @Nullable @PluginAttribute("name") final String name,
      @Nullable @PluginAttribute("minimumBreadcrumbLevel") final Level minimumBreadcrumbLevel,
      @Nullable @PluginAttribute("minimumEventLevel") final Level minimumEventLevel,
      @Nullable @PluginAttribute("minimumLevel") final Level minimumLevel,
      @Nullable @PluginAttribute("dsn") final String dsn,
      @Nullable @PluginAttribute("debug") final Boolean debug,
      @Nullable @PluginElement("filter") final Filter filter,
      @Nullable @PluginAttribute("contextTags") final String contextTags) {

    if (name == null) {
      LOGGER.error("No name provided for SentryAppender");
      return null;
    }
    return new SentryAppender(
        name,
        filter,
        dsn,
        minimumBreadcrumbLevel,
        minimumEventLevel,
        minimumLevel,
        debug,
        null,
        ScopesAdapter.getInstance(),
        contextTags != null ? contextTags.split(",") : null);
  }

  @Override
  public void start() {
    try {
      Sentry.init(
          options -> {
            options.setEnableExternalConfiguration(true);
            options.setInitPriority(InitPriority.LOWEST);
            options.setDsn(dsn);
            if (debug != null) {
              options.setDebug(debug);
            }
            options.setSentryClientName(
                BuildConfig.SENTRY_LOG4J2_SDK_NAME + "/" + BuildConfig.VERSION_NAME);
            options.setSdkVersion(createSdkVersion(options));
            if (contextTags != null) {
              for (final String contextTag : contextTags) {
                options.addContextTag(contextTag);
              }
            }
            Optional.ofNullable(transportFactory).ifPresent(options::setTransportFactory);
          });
    } catch (IllegalArgumentException e) {
      LOGGER.warn("Failed to init Sentry during appender initialization: " + e.getMessage());
    }
    addPackageAndIntegrationInfo();
    super.start();
  }

  @Override
  public void append(final @NotNull LogEvent eventObject) {
    if (eventObject.getLevel().isMoreSpecificThan(minimumLevel)) {
      captureLog(eventObject);
    }
    if (eventObject.getLevel().isMoreSpecificThan(minimumEventLevel)) {
      final Hint hint = new Hint();
      hint.set(SENTRY_SYNTHETIC_EXCEPTION, eventObject);

      scopes.captureEvent(createEvent(eventObject), hint);
    }
    if (eventObject.getLevel().isMoreSpecificThan(minimumBreadcrumbLevel)) {
      final Hint hint = new Hint();
      hint.set(LOG4J_LOG_EVENT, eventObject);

      scopes.addBreadcrumb(createBreadcrumb(eventObject), hint);
    }
  }

  /**
   * Captures a Sentry log from Log4j2's {@link LogEvent}.
   *
   * @param loggingEvent the log4j2 event
   */
  // for the Android compatibility we must use old Java Date class
  @SuppressWarnings("JdkObsolete")
  protected void captureLog(@NotNull LogEvent loggingEvent) {
    final @NotNull SentryLogLevel sentryLevel = toSentryLogLevel(loggingEvent.getLevel());

    final @Nullable Object[] arguments = loggingEvent.getMessage().getParameters();
    final @NotNull SentryAttributes attributes = SentryAttributes.of();

    attributes.add(
        SentryAttribute.stringAttribute(
            "sentry.message.template", loggingEvent.getMessage().getFormat()));

    final @NotNull String formattedMessage = loggingEvent.getMessage().getFormattedMessage();
    final @NotNull SentryLogParameters params = SentryLogParameters.create(attributes);

    Sentry.logger().log(sentryLevel, params, formattedMessage, arguments);
  }

  /**
   * Creates {@link SentryEvent} from Log4j2 {@link LogEvent}.
   *
   * @param loggingEvent the log4j2 event
   * @return the sentry event
   */
  // for the Android compatibility we must use old Java Date class
  @SuppressWarnings("JdkObsolete")
  protected @NotNull SentryEvent createEvent(final @NotNull LogEvent loggingEvent) {
    final SentryEvent event = new SentryEvent(DateUtils.getDateTime(loggingEvent.getTimeMillis()));
    final Message message = new Message();
    message.setMessage(loggingEvent.getMessage().getFormat());
    message.setFormatted(loggingEvent.getMessage().getFormattedMessage());
    message.setParams(toParams(loggingEvent.getMessage().getParameters()));
    event.setMessage(message);
    event.setLogger(loggingEvent.getLoggerName());
    event.setLevel(formatLevel(loggingEvent.getLevel()));

    final ThrowableProxy throwableInformation = loggingEvent.getThrownProxy();
    if (throwableInformation != null) {
      final Mechanism mechanism = new Mechanism();
      mechanism.setType(MECHANISM_TYPE);
      final Throwable mechanismException =
          new ExceptionMechanismException(
              mechanism, throwableInformation.getThrowable(), Thread.currentThread());
      event.setThrowable(mechanismException);
    }

    if (loggingEvent.getThreadName() != null) {
      event.setExtra("thread_name", loggingEvent.getThreadName());
    }

    if (loggingEvent.getMarker() != null) {
      event.setExtra("marker", loggingEvent.getMarker().toString());
    }

    final Map<String, String> contextData =
        CollectionUtils.filterMapEntries(
            loggingEvent.getContextData().toMap(), entry -> entry.getValue() != null);
    if (!contextData.isEmpty()) {
      // get tags from ScopesAdapter options to allow getting the correct tags if Sentry has been
      // initialized somewhere else
      final List<String> contextTags = scopes.getOptions().getContextTags();
      if (contextTags != null && !contextTags.isEmpty()) {
        for (final String contextTag : contextTags) {
          // if mdc tag is listed in SentryOptions, apply as event tag
          if (contextData.containsKey(contextTag)) {
            event.setTag(contextTag, contextData.get(contextTag));
            // remove from all tags applied to logging event
            contextData.remove(contextTag);
          }
        }
      }
      // put the rest of mdc tags in contexts
      if (!contextData.isEmpty()) {
        event.getContexts().put("Context Data", contextData);
      }
    }

    return event;
  }

  private @NotNull List<String> toParams(final @Nullable Object[] arguments) {
    if (arguments != null) {
      return Arrays.stream(arguments)
          .filter(Objects::nonNull)
          .map(Object::toString)
          .collect(Collectors.toList());
    } else {
      return Collections.emptyList();
    }
  }

  /**
   * Creates {@link Breadcrumb} from log4j2 {@link LogEvent}.
   *
   * @param loggingEvent the log4j2 event
   * @return the sentry breadcrumb
   */
  protected @NotNull Breadcrumb createBreadcrumb(final @NotNull LogEvent loggingEvent) {
    final Breadcrumb breadcrumb = new Breadcrumb();
    breadcrumb.setLevel(formatLevel(loggingEvent.getLevel()));
    breadcrumb.setCategory(loggingEvent.getLoggerName());
    breadcrumb.setMessage(loggingEvent.getMessage().getFormattedMessage());
    return breadcrumb;
  }

  /**
   * Transforms a {@link Level} into an {@link SentryLevel}.
   *
   * @param level original level as defined in log4j.
   * @return log level used within sentry.
   */
  private static @NotNull SentryLevel formatLevel(final @NotNull Level level) {
    if (level.isMoreSpecificThan(Level.FATAL)) {
      return SentryLevel.FATAL;
    } else if (level.isMoreSpecificThan(Level.ERROR)) {
      return SentryLevel.ERROR;
    } else if (level.isMoreSpecificThan(Level.WARN)) {
      return SentryLevel.WARNING;
    } else if (level.isMoreSpecificThan(Level.INFO)) {
      return SentryLevel.INFO;
    } else {
      return SentryLevel.DEBUG;
    }
  }

  /**
   * Transforms a {@link Level} into an {@link SentryLogLevel}.
   *
   * @param level original level as defined in log4j.
   * @return log level used within sentry.
   */
  private static @NotNull SentryLogLevel toSentryLogLevel(final @NotNull Level level) {
    if (level.isMoreSpecificThan(Level.FATAL)) {
      return SentryLogLevel.FATAL;
    } else if (level.isMoreSpecificThan(Level.ERROR)) {
      return SentryLogLevel.ERROR;
    } else if (level.isMoreSpecificThan(Level.WARN)) {
      return SentryLogLevel.WARN;
    } else if (level.isMoreSpecificThan(Level.INFO)) {
      return SentryLogLevel.INFO;
    } else if (level.isMoreSpecificThan(Level.DEBUG)) {
      return SentryLogLevel.DEBUG;
    } else {
      return SentryLogLevel.TRACE;
    }
  }

  private @NotNull SdkVersion createSdkVersion(final @NotNull SentryOptions sentryOptions) {
    SdkVersion sdkVersion = sentryOptions.getSdkVersion();

    final String name = BuildConfig.SENTRY_LOG4J2_SDK_NAME;
    final String version = BuildConfig.VERSION_NAME;
    sdkVersion = SdkVersion.updateSdkVersion(sdkVersion, name, version);

    return sdkVersion;
  }

  private void addPackageAndIntegrationInfo() {
    SentryIntegrationPackageStorage.getInstance().addIntegration("Log4j");
  }
}
