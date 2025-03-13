package io.sentry.logback;

import static io.sentry.TypeCheckHint.LOGBACK_LOGGING_EVENT;
import static io.sentry.TypeCheckHint.SENTRY_SYNTHETIC_EXCEPTION;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.encoder.Encoder;
import com.jakewharton.nopen.annotation.Open;
import io.sentry.Breadcrumb;
import io.sentry.DateUtils;
import io.sentry.Hint;
import io.sentry.ITransportFactory;
import io.sentry.InitPriority;
import io.sentry.ScopesAdapter;
import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.SentryIntegrationPackageStorage;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.exception.ExceptionMechanismException;
import io.sentry.protocol.Mechanism;
import io.sentry.protocol.Message;
import io.sentry.protocol.SdkVersion;
import io.sentry.util.CollectionUtils;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Appender for logback in charge of sending the logged events to a Sentry server. */
@Open
public class SentryAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {
  public static final String MECHANISM_TYPE = "LogbackSentryAppender";
  // WARNING: Do not use these options in here, they are only to be used for startup
  private @NotNull SentryOptions options = new SentryOptions();
  private @Nullable ITransportFactory transportFactory;
  private @NotNull Level minimumBreadcrumbLevel = Level.INFO;
  private @NotNull Level minimumEventLevel = Level.ERROR;
  private @Nullable Encoder<ILoggingEvent> encoder;

  @Override
  public void start() {
    if (options.getDsn() == null || !options.getDsn().endsWith("_IS_UNDEFINED")) {
      options.setEnableExternalConfiguration(true);
      options.setInitPriority(InitPriority.LOWEST);
      options.setSentryClientName(
          BuildConfig.SENTRY_LOGBACK_SDK_NAME + "/" + BuildConfig.VERSION_NAME);
      options.setSdkVersion(createSdkVersion(options));
      Optional.ofNullable(transportFactory).ifPresent(options::setTransportFactory);
      try {
        Sentry.init(options);
      } catch (IllegalArgumentException e) {
        addWarn("Failed to init Sentry during appender initialization: " + e.getMessage());
      }
    } else if (!Sentry.isEnabled()) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "DSN is null. SentryAppender is not being initialized");
    }
    addPackageAndIntegrationInfo();
    super.start();
  }

  @Override
  protected void append(@NotNull ILoggingEvent eventObject) {
    if (eventObject.getLevel().isGreaterOrEqual(minimumEventLevel)) {
      final Hint hint = new Hint();
      hint.set(SENTRY_SYNTHETIC_EXCEPTION, eventObject);

      Sentry.captureEvent(createEvent(eventObject), hint);
    }
    if (eventObject.getLevel().isGreaterOrEqual(minimumBreadcrumbLevel)) {
      final Hint hint = new Hint();
      hint.set(LOGBACK_LOGGING_EVENT, eventObject);

      Sentry.addBreadcrumb(createBreadcrumb(eventObject), hint);
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
  protected @NotNull SentryEvent createEvent(@NotNull ILoggingEvent loggingEvent) {
    final SentryEvent event = new SentryEvent(DateUtils.getDateTime(loggingEvent.getTimeStamp()));
    final Message message = new Message();

    // if encoder is set we treat message+params as PII as encoders may be used to mask/strip PII
    if (encoder == null || options.isSendDefaultPii()) {
      message.setMessage(loggingEvent.getMessage());
      message.setParams(toParams(loggingEvent.getArgumentArray()));
    }

    message.setFormatted(formatted(loggingEvent));
    event.setMessage(message);
    event.setLogger(loggingEvent.getLoggerName());
    event.setLevel(formatLevel(loggingEvent.getLevel()));

    if (loggingEvent.getThrowableProxy() instanceof ThrowableProxy) {
      final ThrowableProxy throwableInformation = (ThrowableProxy) loggingEvent.getThrowableProxy();
      if (throwableInformation != null) {
        final Mechanism mechanism = new Mechanism();
        mechanism.setType(MECHANISM_TYPE);
        final Throwable mechanismException =
            new ExceptionMechanismException(
                mechanism, throwableInformation.getThrowable(), Thread.currentThread());
        event.setThrowable(mechanismException);
      }
    }

    if (loggingEvent.getThreadName() != null) {
      event.setExtra("thread_name", loggingEvent.getThreadName());
    }

    if (loggingEvent.getMarker() != null) {
      event.setExtra("marker", loggingEvent.getMarker().toString());
    }

    // remove keys with null values, there is no sense to send these keys to Sentry
    final Map<String, String> mdcProperties =
        CollectionUtils.filterMapEntries(
            loggingEvent.getMDCPropertyMap(), entry -> entry.getValue() != null);
    if (!mdcProperties.isEmpty()) {
      // get tags from ScopesAdapter options to allow getting the correct tags if Sentry has been
      // initialized somewhere else
      final List<String> contextTags = ScopesAdapter.getInstance().getOptions().getContextTags();
      if (!contextTags.isEmpty()) {
        for (final String contextTag : contextTags) {
          // if mdc tag is listed in SentryOptions, apply as event tag
          if (mdcProperties.containsKey(contextTag)) {
            event.setTag(contextTag, mdcProperties.get(contextTag));
            // remove from all tags applied to logging event
            mdcProperties.remove(contextTag);
          }
        }
      }
      // put the rest of mdc tags in contexts
      if (!mdcProperties.isEmpty()) {
        event.getContexts().put("MDC", mdcProperties);
      }
    }

    return event;
  }

  private String formatted(@NotNull ILoggingEvent loggingEvent) {
    if (encoder != null) {
      try {
        return new String(encoder.encode(loggingEvent), StandardCharsets.UTF_8);
      } catch (final Throwable t) {
        // catch exceptions from possibly incorrectly configured encoder
        // and fallback to default formatted message
        addWarn("Failed to encode logging event", t);
      }
    }
    return loggingEvent.getFormattedMessage();
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
  protected @NotNull Breadcrumb createBreadcrumb(final @NotNull ILoggingEvent loggingEvent) {
    final Breadcrumb breadcrumb = new Breadcrumb();
    breadcrumb.setLevel(formatLevel(loggingEvent.getLevel()));
    breadcrumb.setCategory(loggingEvent.getLoggerName());
    breadcrumb.setMessage(formatted(loggingEvent));
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

    final String name = BuildConfig.SENTRY_LOGBACK_SDK_NAME;
    final String version = BuildConfig.VERSION_NAME;
    sdkVersion = SdkVersion.updateSdkVersion(sdkVersion, name, version);

    return sdkVersion;
  }

  private void addPackageAndIntegrationInfo() {
    SentryIntegrationPackageStorage.getInstance()
        .addPackage("maven:io.sentry:sentry-logback", BuildConfig.VERSION_NAME);
    SentryIntegrationPackageStorage.getInstance().addIntegration("Logback");
  }

  public void setOptions(final @Nullable SentryOptions options) {
    if (options != null) {
      this.options = options;
    }
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
  void setTransportFactory(final @Nullable ITransportFactory transportFactory) {
    this.transportFactory = transportFactory;
  }

  public void setEncoder(Encoder<ILoggingEvent> encoder) {
    this.encoder = encoder;
  }
}
