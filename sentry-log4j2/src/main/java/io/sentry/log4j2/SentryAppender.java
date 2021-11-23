package io.sentry.log4j2;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.Breadcrumb;
import io.sentry.DateUtils;
import io.sentry.HubAdapter;
import io.sentry.IHub;
import io.sentry.ITransportFactory;
import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
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
  private final @Nullable String dsn;
  private final @Nullable ITransportFactory transportFactory;
  private @NotNull Level minimumBreadcrumbLevel = Level.INFO;
  private @NotNull Level minimumEventLevel = Level.ERROR;
  private final @Nullable Boolean debug;
  private final @NotNull IHub hub;

  public SentryAppender(
      final @NotNull String name,
      final @Nullable Filter filter,
      final @Nullable String dsn,
      final @Nullable Level minimumBreadcrumbLevel,
      final @Nullable Level minimumEventLevel,
      final @Nullable Boolean debug,
      final @Nullable ITransportFactory transportFactory,
      final @NotNull IHub hub) {
    super(name, filter, null, true, null);
    this.dsn = dsn;
    if (minimumBreadcrumbLevel != null) {
      this.minimumBreadcrumbLevel = minimumBreadcrumbLevel;
    }
    if (minimumEventLevel != null) {
      this.minimumEventLevel = minimumEventLevel;
    }
    this.debug = debug;
    this.transportFactory = transportFactory;
    this.hub = hub;
  }

  /**
   * Create a Sentry Appender.
   *
   * @param name The name of the Appender.
   * @param minimumBreadcrumbLevel The min. level of the breadcrumb.
   * @param minimumEventLevel The min. level of the event.
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
      @Nullable @PluginAttribute("dsn") final String dsn,
      @Nullable @PluginAttribute("debug") final Boolean debug,
      @Nullable @PluginElement("filter") final Filter filter) {

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
        debug,
        null,
        HubAdapter.getInstance());
  }

  @Override
  public void start() {
    if (!Sentry.isEnabled()) {
      try {
        Sentry.init(
            options -> {
              options.setEnableExternalConfiguration(true);
              options.setDsn(dsn);
              if (debug != null) {
                options.setDebug(debug);
              }
              options.setSentryClientName(BuildConfig.SENTRY_LOG4J2_SDK_NAME);
              options.setSdkVersion(createSdkVersion(options));
              Optional.ofNullable(transportFactory).ifPresent(options::setTransportFactory);
            });
      } catch (IllegalArgumentException e) {
        LOGGER.info("Failed to init Sentry during appender initialization: " + e.getMessage());
      }
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
      event.setThrowable(throwableInformation.getThrowable());
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

  private @NotNull SdkVersion createSdkVersion(final @NotNull SentryOptions sentryOptions) {
    SdkVersion sdkVersion = sentryOptions.getSdkVersion();

    final String name = BuildConfig.SENTRY_LOG4J2_SDK_NAME;
    final String version = BuildConfig.VERSION_NAME;
    sdkVersion = SdkVersion.updateSdkVersion(sdkVersion, name, version);

    sdkVersion.addPackage("maven:io.sentry:sentry-log4j2", version);

    return sdkVersion;
  }
}
