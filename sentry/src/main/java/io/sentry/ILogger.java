package io.sentry;

import org.jetbrains.annotations.Nullable;

/** Sentry SDK internal logging interface. */
public interface ILogger {

  /**
   * Logs a message with the specified level, message and optional arguments.
   *
   * @param level The SentryLevel.
   * @param message The message.
   * @param args The optional arguments to format the message.
   */
  void log(SentryLevel level, String message, Object... args);

  /**
   * Logs a message with the specified level, message and optional arguments.
   *
   * @param level The SentryLevel.
   * @param message The message.
   * @param throwable The throwable to log.
   */
  void log(SentryLevel level, String message, Throwable throwable);

  /**
   * Logs a message with the specified level, throwable, message and optional arguments.
   *
   * @param level The SentryLevel.
   * @param throwable The throwable to log.
   * @param message The message.
   * @param args the formatting arguments
   */
  void log(SentryLevel level, Throwable throwable, String message, Object... args);

  /**
   * Whether this logger is enabled for the specified SentryLevel.
   *
   * @param level The SentryLevel to test against.
   * @return True if a log message would be recorded for the level. Otherwise false.
   */
  default boolean isEnabled(final @Nullable SentryLevel level) {
    return true;
  }
}
