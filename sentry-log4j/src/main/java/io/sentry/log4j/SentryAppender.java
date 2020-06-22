package io.sentry.log4j;

import io.sentry.core.Sentry;
import io.sentry.core.SentryEvent;
import io.sentry.core.SentryLevel;
import io.sentry.core.protocol.Message;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.spi.ErrorCode;
import org.apache.log4j.spi.Filter;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.ThrowableInformation;

import java.util.Date;

/**
 * This is a Log4j appender that will send the log messages to Sentry.
 */
public class SentryAppender extends AppenderSkeleton {
  /** Name of the {@link SentryEvent#setExtra(String, Object)} property containing NDC details. */
  public static final String LOG4J_NDC = "log4j-NDC";

  /** Tells whether we're currently capturing a log statement or not */
  private final ThreadLocal<Boolean> capturing = ThreadLocal.withInitial(() -> false);

  /** Creates an instance of SentryAppender. */
  public SentryAppender() {
    this.addFilter(new DropSentryFilter());
  }

  /**
   * Transforms a {@link Level} into an {@link SentryLevel}.
   *
   * @param level original level as defined in log4j.
   * @return log level used within sentry.
   */
  protected static SentryLevel formatLevel(Level level) {
    if (level.isGreaterOrEqual(Level.FATAL)) {
      return SentryLevel.FATAL;
    } else if (level.isGreaterOrEqual(Level.ERROR)) {
      return SentryLevel.ERROR;
    } else if (level.isGreaterOrEqual(Level.WARN)) {
      return SentryLevel.WARNING;
    } else if (level.isGreaterOrEqual(Level.INFO)) {
      return SentryLevel.INFO;
    } else if (level.isGreaterOrEqual(Level.ALL)) {
      return SentryLevel.DEBUG;
    } else {
      return SentryLevel.LOG;
    }
  }

  @Override
  protected void append(LoggingEvent loggingEvent) {
    if (!Sentry.isEnabled()) {
      return;
    }

    // Do not log the event if we're in the middle of sentry logging using this logger. We would
    // most probably end up in an infinite loop.
    if (capturing.get()) {
      System.err.println("Sentry: The logger configured in Sentry options writes to the Sentry Log4j appender. This" +
              " is illegal because we would now most probably enter an infinite loop. Please use a logger with" +
              " a different appender for this. The message was: " +
              loggingEvent.getRenderedMessage());
      return;
    }

    capturing.set(true);
    try {
      Sentry.captureEvent(createEvent(loggingEvent));
    } catch (RuntimeException e) {
      getErrorHandler()
          .error(
              "An exception occurred while creating a new event in Sentry",
              e,
              ErrorCode.WRITE_FAILURE);
    } finally {
      capturing.set(false);
    }
  }

    /**
     * Builds an EventBuilder based on the logging event.
     *
     * @param loggingEvent Log generated.
     * @return EventBuilder containing details provided by the logging system.
     */
    protected SentryEvent createEvent(LoggingEvent loggingEvent) {
      SentryEvent event = new SentryEvent(new Date(loggingEvent.getTimeStamp()));
      Message message = new Message();
      message.setFormatted(loggingEvent.getRenderedMessage());
      event.setMessage(message);
      event.setLogger(loggingEvent.getLoggerName());
      event.setLevel(formatLevel(loggingEvent.getLevel()));

      ThrowableInformation throwableInformation = loggingEvent.getThrowableInformation();
      if (throwableInformation != null) {
        event.setThrowable(throwableInformation.getThrowable());
      }

      // We could use the loggingEvent.getLocationInformation() to get the location information even for just a simple
      // log message.
      // I am not sure if that is a right thing to do though, because a) users probably don't expect that info because
      // it's not usually present in the log files either and b) location info in the logging event is potentially lazy
      // initialized to the location of the current callsite (e.g. here) which is not what the user would expect.

      if (loggingEvent.getNDC() != null) {
        event.setExtra(LOG4J_NDC, loggingEvent.getNDC());
      }

      return event;
    }

  @Override
  public void close() {
    this.closed = true;
  }

  @Override
  public boolean requiresLayout() {
    return false;
  }

  private class DropSentryFilter extends Filter {
    @Override
    public int decide(LoggingEvent event) {
      String loggerName = event.getLoggerName();
      if (loggerName != null && loggerName.startsWith("io.sentry")) {
        return Filter.DENY;
      }
      return Filter.NEUTRAL;
    }
  }
}
