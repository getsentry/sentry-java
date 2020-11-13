package io.sentry.jul;

import io.sentry.DateUtils;
import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.protocol.Message;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.ErrorManager;
import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

/**
 * Logging handler in charge of sending the java.util.logging records to a Sentry server.
 */
public final class SentryHandler extends Handler {
  /**
   * Name of the {@link SentryEvent} extra property containing the Thread id.
   */
  public static final String THREAD_ID = "Sentry-ThreadId";
  /**
   * If true, <code>String.format()</code> is used to render parameterized log
   * messages instead of <code>MessageFormat.format()</code>; Defaults to
   * false.
   */
  protected boolean printfStyle;

  /**
   * Creates an instance of SentryHandler.
   */
  public SentryHandler() {
    retrieveProperties();
    this.setFilter(new DropSentryFilter());
  }



  /**
   * Transforms a {@link Level} into an {@link SentryLevel}.
   *
   * @param level original level as defined in JUL.
   * @return log level used within sentry.
   */
  protected static @Nullable SentryLevel getLevel(final @NotNull Level level) {
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

  /**
   * Extracts message parameters into a List of Strings.
   * <p>
   * null parameters are kept as null.
   *
   * @param parameters parameters provided to the logging system.
   * @return the parameters formatted as Strings in a List.
   */
  protected static @NotNull List<String> formatMessageParameters(final @NotNull Object[] parameters) {
    final List<String> formattedParameters = new ArrayList<>(parameters.length);
    for (Object parameter : parameters) {
      formattedParameters.add((parameter != null) ? parameter.toString() : null);
    }
    return formattedParameters;
  }

  /**
   * Retrieves the properties of the logger.
   */
  protected void retrieveProperties() {
    final LogManager manager = LogManager.getLogManager();
    final String className = SentryHandler.class.getName();
    setPrintfStyle(Boolean.parseBoolean(manager.getProperty(className + ".printfStyle")));
    setLevel(parseLevelOrDefault(manager.getProperty(className + ".level")));
  }

  private @NotNull Level parseLevelOrDefault(final @NotNull String levelName) {
    try {
      return Level.parse(levelName.trim());
    } catch (RuntimeException e) {
      return Level.WARNING;
    }
  }

  @Override
  public void publish(final @NotNull LogRecord record) {
    // Do not log the event if the current thread is managed by sentry
    if (!isLoggable(record)) {
      return;
    }

    try {
      final SentryEvent event = createEventBuilder(record);
      Sentry.captureEvent(event);
    } catch (RuntimeException e) {
      reportError("An exception occurred while creating a new event in Sentry", e, ErrorManager.WRITE_FAILURE);
    }
  }

  /**
   * Builds an EventBuilder based on the log record.
   *
   * @param record Log generated.
   * @return EventBuilder containing details provided by the logging system.
   */
  protected @NotNull SentryEvent createEventBuilder(final @NotNull LogRecord record) {
    final SentryEvent event =
      new SentryEvent(DateUtils.getDateTime(new Date(record.getMillis())));
    event.setLevel(getLevel(record.getLevel()));
    event.setLogger(record.getLoggerName());

    final Message sentryMessage = new Message();
    sentryMessage.setParams(toParams(record.getParameters()));

    String message = record.getMessage();
    if (record.getResourceBundle() != null && record.getResourceBundle().containsKey(record.getMessage())) {
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
      event.setThrowable(throwable);
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
   * Returns formatted Event message when provided the message template and
   * parameters.
   *
   * @param message Message template body.
   * @param parameters Array of parameters for the message.
   * @return Formatted message.
   */
  protected String formatMessage(final @NotNull String message, final @Nullable Object[] parameters) {
    String formatted;
    if (printfStyle) {
      formatted = String.format(message, parameters);
    } else {
      formatted = MessageFormat.format(message, parameters);
    }
    return formatted;
  }

  @Override
  public void flush() {
  }

  @Override
  public void close() throws SecurityException {
    try {
      Sentry.close();
    } catch (RuntimeException e) {
      reportError("An exception occurred while closing the Sentry connection", e, ErrorManager.CLOSE_FAILURE);
    }
  }

  public void setPrintfStyle(final boolean printfStyle) {
    this.printfStyle = printfStyle;
  }

  private static final class DropSentryFilter implements Filter {
    @Override
    public boolean isLoggable(final @NotNull LogRecord record) {
      final String loggerName = record.getLoggerName();
      return loggerName == null || !loggerName.startsWith("io.sentry");
    }
  }
}
