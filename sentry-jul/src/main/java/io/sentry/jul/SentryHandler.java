package io.sentry.jul;

import static io.sentry.TypeCheckHint.JUL_LOG_RECORD;
import static io.sentry.TypeCheckHint.SENTRY_SYNTHETIC_EXCEPTION;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.Breadcrumb;
import io.sentry.Hint;
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
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.logging.ErrorManager;
import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.slf4j.MDC;

/** Logging handler in charge of sending the java.util.logging records to a Sentry server. */
@Open
public class SentryHandler extends Handler {
  public static final String MECHANISM_TYPE = "JulSentryHandler";
  /** Name of the {@link SentryEvent} extra property containing the Thread id. */
  public static final String THREAD_ID = "thread_id";
  /**
   * If true, <code>String.format()</code> is used to render parameterized log messages instead of
   * <code>MessageFormat.format()</code>; Defaults to false.
   */
  private boolean printfStyle;

  private @NotNull Level minimumBreadcrumbLevel = Level.INFO;
  private @NotNull Level minimumEventLevel = Level.SEVERE;

  /** Creates an instance of SentryHandler. */
  public SentryHandler() {
    this(new SentryOptions());
  }

  /**
   * Creates an instance of SentryHandler.
   *
   * @param options the SentryOptions
   */
  public SentryHandler(final @NotNull SentryOptions options) {
    this(options, true, true);
  }

  /**
   * Creates an instance of SentryHandler.
   *
   * @param options the SentryOptions
   * @param enableExternalConfiguration whether external options like sentry.properties and ENV vars
   *     should be parsed
   */
  public SentryHandler(
      final @NotNull SentryOptions options, final boolean enableExternalConfiguration) {
    this(options, true, enableExternalConfiguration);
  }

  /** Creates an instance of SentryHandler. */
  @TestOnly
  SentryHandler(
      final @NotNull SentryOptions options,
      final boolean configureFromLogManager,
      final boolean enableExternalConfiguration) {
    setFilter(new DropSentryFilter());
    if (configureFromLogManager) {
      retrieveProperties();
    }
    options.setEnableExternalConfiguration(enableExternalConfiguration);
    options.setInitPriority(InitPriority.LOWEST);
    options.setSdkVersion(createSdkVersion(options));
    Sentry.init(options);
    addPackageAndIntegrationInfo();
  }

  @Override
  public void publish(final @NotNull LogRecord record) {
    // Do not log the event if the current thread is managed by sentry
    if (!isLoggable(record)) {
      return;
    }
    try {
      if (record.getLevel().intValue() >= minimumEventLevel.intValue()) {
        final Hint hint = new Hint();
        hint.set(SENTRY_SYNTHETIC_EXCEPTION, record);

        Sentry.captureEvent(createEvent(record), hint);
      }
      if (record.getLevel().intValue() >= minimumBreadcrumbLevel.intValue()) {
        final Hint hint = new Hint();
        hint.set(JUL_LOG_RECORD, record);

        Sentry.addBreadcrumb(createBreadcrumb(record), hint);
      }
    } catch (RuntimeException e) {
      reportError(
          "An exception occurred while creating a new event in Sentry",
          e,
          ErrorManager.WRITE_FAILURE);
    }
  }

  /** Retrieves the properties of the logger. */
  private void retrieveProperties() {
    final LogManager manager = LogManager.getLogManager();
    final String className = SentryHandler.class.getName();
    setPrintfStyle(Boolean.parseBoolean(manager.getProperty(className + ".printfStyle")));
    setLevel(parseLevelOrDefault(manager.getProperty(className + ".level")));
    final String minimumBreadCrumbLevel =
        manager.getProperty(className + ".minimumBreadcrumbLevel");
    if (minimumBreadCrumbLevel != null) {
      setMinimumBreadcrumbLevel(parseLevelOrDefault(minimumBreadCrumbLevel));
    }
    final String minimumEventLevel = manager.getProperty(className + ".minimumEventLevel");
    if (minimumEventLevel != null) {
      setMinimumEventLevel(parseLevelOrDefault(minimumEventLevel));
    }
  }

  /**
   * Transforms a {@link Level} into an {@link SentryLevel}.
   *
   * @param level original level as defined in JUL.
   * @return log level used within sentry.
   */
  private static @Nullable SentryLevel formatLevel(final @NotNull Level level) {
    if (level.intValue() >= Level.SEVERE.intValue()) {
      return SentryLevel.ERROR;
    } else if (level.intValue() >= Level.WARNING.intValue()) {
      return SentryLevel.WARNING;
    } else if (level.intValue() >= Level.INFO.intValue()) {
      return SentryLevel.INFO;
    } else if (level.intValue() >= Level.ALL.intValue()) {
      return SentryLevel.DEBUG;
    } else {
      return null;
    }
  }

  private @NotNull Level parseLevelOrDefault(final @NotNull String levelName) {
    try {
      return Level.parse(levelName.trim());
    } catch (RuntimeException e) {
      return Level.WARNING;
    }
  }

  private @NotNull Breadcrumb createBreadcrumb(final @NotNull LogRecord record) {
    final Breadcrumb breadcrumb = new Breadcrumb();
    breadcrumb.setLevel(formatLevel(record.getLevel()));
    breadcrumb.setCategory(record.getLoggerName());
    if (record.getParameters() != null) {
      try {
        breadcrumb.setMessage(formatMessage(record.getMessage(), record.getParameters()));
      } catch (RuntimeException e) {
        breadcrumb.setMessage(record.getMessage());
      }
    } else {
      breadcrumb.setMessage(record.getMessage());
    }
    return breadcrumb;
  }

  /**
   * Creates {@link SentryEvent} from JUL's {@link LogRecord}.
   *
   * @param record the log record
   * @return the sentry event
   */
  // for the Android compatibility we must use old Java Date class
  @SuppressWarnings({"JdkObsolete", "JavaUtilDate", "deprecation"})
  @NotNull
  SentryEvent createEvent(final @NotNull LogRecord record) {
    final SentryEvent event = new SentryEvent(new Date(record.getMillis()));
    event.setLevel(formatLevel(record.getLevel()));
    event.setLogger(record.getLoggerName());

    final Message sentryMessage = new Message();
    sentryMessage.setParams(toParams(record.getParameters()));

    String message = record.getMessage();
    if (record.getResourceBundle() != null
        && record.getResourceBundle().containsKey(record.getMessage())) {
      message = record.getResourceBundle().getString(record.getMessage());
    }
    sentryMessage.setMessage(message);
    if (record.getParameters() != null) {
      try {
        sentryMessage.setFormatted(formatMessage(message, record.getParameters()));
      } catch (RuntimeException e) {
        // local formatting failed, send message and parameters without formatted string
      }
    }
    event.setMessage(sentryMessage);

    final Throwable throwable = record.getThrown();
    if (throwable != null) {
      final Mechanism mechanism = new Mechanism();
      mechanism.setType(MECHANISM_TYPE);
      final Throwable mechanismException =
          new ExceptionMechanismException(mechanism, throwable, Thread.currentThread());
      event.setThrowable(mechanismException);
    }
    Map<String, String> mdcProperties = MDC.getMDCAdapter().getCopyOfContextMap();
    if (mdcProperties != null) {
      mdcProperties =
          CollectionUtils.filterMapEntries(mdcProperties, entry -> entry.getValue() != null);
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
    }
    event.setExtra(THREAD_ID, record.getThreadID());
    return event;
  }

  private @NotNull List<String> toParams(final @Nullable Object[] arguments) {
    final List<String> result = new ArrayList<>();
    if (arguments != null) {
      for (Object argument : arguments) {
        if (argument != null) {
          result.add(argument.toString());
        }
      }
    }
    return result;
  }

  /**
   * Returns formatted Event message when provided the message template and parameters.
   *
   * @param message Message template body.
   * @param parameters Array of parameters for the message.
   * @return Formatted message.
   */
  private @NotNull String formatMessage(
      final @NotNull String message, final @Nullable Object[] parameters) {
    String formatted;
    if (printfStyle) {
      formatted = String.format(message, parameters);
    } else {
      formatted = MessageFormat.format(message, parameters);
    }
    return formatted;
  }

  @Override
  public void flush() {}

  @Override
  public void close() throws SecurityException {
    try {
      Sentry.close();
    } catch (RuntimeException e) {
      reportError(
          "An exception occurred while closing the Sentry connection",
          e,
          ErrorManager.CLOSE_FAILURE);
    }
  }

  private @NotNull SdkVersion createSdkVersion(final @NotNull SentryOptions sentryOptions) {
    SdkVersion sdkVersion = sentryOptions.getSdkVersion();

    final String name = BuildConfig.SENTRY_JUL_SDK_NAME;
    final String version = BuildConfig.VERSION_NAME;

    sdkVersion = SdkVersion.updateSdkVersion(sdkVersion, name, version);

    return sdkVersion;
  }

  private void addPackageAndIntegrationInfo() {
    SentryIntegrationPackageStorage.getInstance()
        .addPackage("maven:io.sentry:sentry-jul", BuildConfig.VERSION_NAME);
    SentryIntegrationPackageStorage.getInstance().addIntegration("Jul");
  }

  public void setPrintfStyle(final boolean printfStyle) {
    this.printfStyle = printfStyle;
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

  public boolean isPrintfStyle() {
    return printfStyle;
  }

  private static final class DropSentryFilter implements Filter {
    @Override
    public boolean isLoggable(final @NotNull LogRecord record) {
      final String loggerName = record.getLoggerName();
      return loggerName == null
          || !loggerName.startsWith("io.sentry")
          || loggerName.startsWith("io.sentry.samples");
    }
  }
}
