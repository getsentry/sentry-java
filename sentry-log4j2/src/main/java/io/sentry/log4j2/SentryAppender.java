package io.sentry.log4j2;

import io.sentry.core.Breadcrumb;
import io.sentry.core.DateUtils;
import io.sentry.core.HubAdapter;
import io.sentry.core.IHub;
import io.sentry.core.Sentry;
import io.sentry.core.SentryEvent;
import io.sentry.core.SentryLevel;
import io.sentry.core.SentryOptions;
import io.sentry.core.protocol.Message;
import io.sentry.core.protocol.SdkVersion;
import io.sentry.core.transport.ITransport;
import io.sentry.core.util.CollectionUtils;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
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
public final class SentryAppender extends AbstractAppender {
  private final @Nullable String dsn;
  private final @Nullable ITransport transport;
  private @NotNull Level minimumBreadcrumbLevel = Level.INFO;
  private @NotNull Level minimumEventLevel = Level.ERROR;
  private final @NotNull IHub hub;

  public SentryAppender(
      final @NotNull String name,
      final @Nullable Filter filter,
      final @Nullable String dsn,
      final @Nullable Level minimumBreadcrumbLevel,
      final @Nullable Level minimumEventLevel,
      final @Nullable ITransport transport,
      final @NotNull IHub hub) {
    super(name, filter, null, true, null);
    this.dsn = dsn;
    if (minimumBreadcrumbLevel != null) {
      this.minimumBreadcrumbLevel = minimumBreadcrumbLevel;
    }
    if (minimumEventLevel != null) {
      this.minimumEventLevel = minimumEventLevel;
    }
    this.transport = transport;
    this.hub = hub;
  }

  /**
   * Create a Sentry Appender.
   *
   * @param name The name of the Appender.
   * @param filter The filter, if any, to use.
   * @return The SentryAppender.
   */
  @PluginFactory
  public static SentryAppender createAppender(
      @PluginAttribute("name") final String name,
      @PluginAttribute("minimumBreadcrumbLevel") final Level minimumBreadcrumbLevel,
      @PluginAttribute("minimumEventLevel") final Level minimumEventLevel,
      @PluginAttribute("dsn") final String dsn,
      @PluginElement("filter") final Filter filter) {

    if (name == null) {
      LOGGER.error("No name provided for SentryAppender");
      return null;
    }
    return new SentryAppender(name, filter, dsn, minimumBreadcrumbLevel, minimumEventLevel, null, HubAdapter.getInstance());
  }

  @Override
  public void start() {
    if (dsn != null) {
      Sentry.init(
          options -> {
            options.setDsn(dsn);
            options.setSentryClientName(BuildConfig.SENTRY_LOG4J2_SDK_NAME);
            options.setSdkVersion(createSdkVersion(options));
            Optional.ofNullable(transport).ifPresent(options::setTransport);
          });
    }
    super.start();
  }

  @Override
  public void append(final @NotNull LogEvent eventObject) {
    if (eventObject.getLevel().isMoreSpecificThan(minimumEventLevel)) {
      hub.captureEvent(createEvent(eventObject));
    }
    if (eventObject.getLevel().isMoreSpecificThan(minimumBreadcrumbLevel)) {
      hub.addBreadcrumb(createBreadcrumb(eventObject));
    }
  }

  /**
   * Creates {@link SentryEvent} from Log4j2 {@link LogEvent}.
   *
   * @param loggingEvent the log4j2 event
   * @return the sentry event
   */
  // for the Android compatibility we must use old Java Date class
  @SuppressWarnings("JdkObsolete")
  final @NotNull SentryEvent createEvent(final @NotNull LogEvent loggingEvent) {
    final SentryEvent event =
        new SentryEvent(DateUtils.getDateTime(new Date(loggingEvent.getTimeMillis())));
    final Message message = new Message();
    message.setMessage(loggingEvent.getMessage().getFormat());
    message.setFormatted(loggingEvent.getMessage().getFormattedMessage());
    message.setParams(toParams(loggingEvent.getMessage().getParameters()));
    event.setMessage(message);
    event.setLogger(loggingEvent.getLoggerName());
    event.setLevel(formatLevel(loggingEvent.getLevel()));

    final ThrowableProxy throwableInformation = loggingEvent.getThrownProxy();
    if (throwableInformation != null) {
      event.setThrowable(throwableInformation.getThrowable());
    }

    if (loggingEvent.getThreadName() != null) {
      event.setExtra("thread_name", loggingEvent.getThreadName());
    }

    final Map<String, String> contextData =
        CollectionUtils.shallowCopy(loggingEvent.getContextData().toMap());
    if (!contextData.isEmpty()) {
      event.getContexts().put("Context Data", contextData);
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
  private @NotNull Breadcrumb createBreadcrumb(final @NotNull LogEvent loggingEvent) {
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

  private @NotNull SdkVersion createSdkVersion(final @NotNull SentryOptions sentryOptions) {
    SdkVersion sdkVersion = sentryOptions.getSdkVersion();

    if (sdkVersion == null) {
      sdkVersion = new SdkVersion();
    }

    sdkVersion.setName(BuildConfig.SENTRY_LOG4J2_SDK_NAME);
    final String version = BuildConfig.VERSION_NAME;
    sdkVersion.setVersion(version);
    sdkVersion.addPackage("maven:sentry-log4j2", version);

    return sdkVersion;
  }
}
