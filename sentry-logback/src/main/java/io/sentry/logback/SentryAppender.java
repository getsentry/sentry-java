package io.sentry.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import io.sentry.Breadcrumb;
import io.sentry.DateUtils;
import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.protocol.Message;
import io.sentry.protocol.SdkVersion;
import io.sentry.transport.ITransport;
import io.sentry.util.CollectionUtils;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Appender for logback in charge of sending the logged events to a Sentry server. */
public final class SentryAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {
  private @Nullable SentryOptions options;
  private @Nullable ITransport transport;
  private @NotNull Level minimumBreadcrumbLevel = Level.INFO;
  private @NotNull Level minimumEventLevel = Level.ERROR;

  @Override
  public void start() {
    if (options != null && options.getDsn() != null) {
      options.setSentryClientName(BuildConfig.SENTRY_LOGBACK_SDK_NAME);
      options.setSdkVersion(createSdkVersion(options));
      Optional.ofNullable(transport).ifPresent(options::setTransport);
      Sentry.init(options);
    }
    super.start();
  }

  @Override
  protected void append(@NotNull ILoggingEvent eventObject) {
    if (eventObject.getLevel().isGreaterOrEqual(minimumEventLevel)) {
      Sentry.captureEvent(createEvent(eventObject));
    }
    if (eventObject.getLevel().isGreaterOrEqual(minimumBreadcrumbLevel)) {
      Sentry.addBreadcrumb(createBreadcrumb(eventObject));
    }
  }

  /**
   * Creates {@link SentryEvent} from Logback's {@link ILoggingEvent}.
   *
   * @param loggingEvent the logback event
   * @return the sentry event
   */
  // for the Android compatibility we must use old Java Date class
  @SuppressWarnings("JdkObsolete")
  final @NotNull SentryEvent createEvent(@NotNull ILoggingEvent loggingEvent) {
    final SentryEvent event =
        new SentryEvent(DateUtils.getDateTime(new Date(loggingEvent.getTimeStamp())));
    final Message message = new Message();
    message.setMessage(loggingEvent.getMessage());
    message.setFormatted(loggingEvent.getFormattedMessage());
    message.setParams(toParams(loggingEvent.getArgumentArray()));
    event.setMessage(message);
    event.setLogger(loggingEvent.getLoggerName());
    event.setLevel(formatLevel(loggingEvent.getLevel()));

    final ThrowableProxy throwableInformation = (ThrowableProxy) loggingEvent.getThrowableProxy();
    if (throwableInformation != null) {
      event.setThrowable(throwableInformation.getThrowable());
    }

    if (loggingEvent.getThreadName() != null) {
      event.setExtra("thread_name", loggingEvent.getThreadName());
    }

    final Map<String, String> mdcProperties =
        CollectionUtils.shallowCopy(loggingEvent.getMDCPropertyMap());
    if (!mdcProperties.isEmpty()) {
      event.getContexts().put("MDC", mdcProperties);
    }

    return event;
  }

  private @NotNull List<String> toParams(@Nullable Object[] arguments) {
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
   * Creates {@link Breadcrumb} from Logback's {@link ILoggingEvent}.
   *
   * @param loggingEvent the logback event
   * @return the sentry breadcrumb
   */
  private @NotNull Breadcrumb createBreadcrumb(final @NotNull ILoggingEvent loggingEvent) {
    final Breadcrumb breadcrumb = new Breadcrumb();
    breadcrumb.setLevel(formatLevel(loggingEvent.getLevel()));
    breadcrumb.setCategory(loggingEvent.getLoggerName());
    breadcrumb.setMessage(loggingEvent.getFormattedMessage());
    return breadcrumb;
  }

  /**
   * Transforms a {@link Level} into an {@link SentryLevel}.
   *
   * @param level original level as defined in log4j.
   * @return log level used within sentry.
   */
  private static @NotNull SentryLevel formatLevel(@NotNull Level level) {
    if (level.isGreaterOrEqual(Level.ERROR)) {
      return SentryLevel.ERROR;
    } else if (level.isGreaterOrEqual(Level.WARN)) {
      return SentryLevel.WARNING;
    } else if (level.isGreaterOrEqual(Level.INFO)) {
      return SentryLevel.INFO;
    } else {
      return SentryLevel.DEBUG;
    }
  }

  private @NotNull SdkVersion createSdkVersion(@NotNull SentryOptions sentryOptions) {
    SdkVersion sdkVersion = sentryOptions.getSdkVersion();

    if (sdkVersion == null) {
      sdkVersion = new SdkVersion();
    }

    sdkVersion.setName(BuildConfig.SENTRY_LOGBACK_SDK_NAME);
    final String version = BuildConfig.VERSION_NAME;
    sdkVersion.setVersion(version);
    sdkVersion.addPackage("maven:sentry-logback", version);

    return sdkVersion;
  }

  public void setOptions(final @Nullable SentryOptions options) {
    this.options = options;
  }

  public void setMinimumBreadcrumbLevel(final @Nullable Level minimumBreadcrumbLevel) {
    if (minimumBreadcrumbLevel != null) {
      this.minimumBreadcrumbLevel = minimumBreadcrumbLevel;
    }
  }

  public @NotNull Level getMinimumBreadcrumbLevel() {
    return minimumBreadcrumbLevel;
  }

  public void setMinimumEventLevel(final @Nullable Level minimumEventLevel) {
    if (minimumEventLevel != null) {
      this.minimumEventLevel = minimumEventLevel;
    }
  }

  public @NotNull Level getMinimumEventLevel() {
    return minimumEventLevel;
  }

  @ApiStatus.Internal
  void setTransport(@Nullable ITransport transport) {
    this.transport = transport;
  }
}
