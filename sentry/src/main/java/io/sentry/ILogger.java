package io.sentry;

import org.jetbrains.annotations.NotNull;
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
  void log(@NotNull SentryLevel level, @NotNull String message, @Nullable Object... args);

  /**
   * Logs a message without arguments. This overload and the overloads with one and two arguments do
   * not allocate a varargs array at the call site. Thus, a disabled logger costs nothing.
   *
   * @param level The SentryLevel.
   * @param message The message.
   */
  default void log(final @NotNull SentryLevel level, final @NotNull String message) {
    log(level, message, new Object[0]);
  }

  /**
   * Logs a message with one formatting argument.
   *
   * @param level The SentryLevel.
   * @param message The message.
   * @param arg The argument to format the message.
   */
  default void log(
      final @NotNull SentryLevel level, final @NotNull String message, final @Nullable Object arg) {
    log(level, message, new Object[] {arg});
  }

  /**
   * Logs a message with two formatting arguments.
   *
   * @param level The SentryLevel.
   * @param message The message.
   * @param arg1 The first argument to format the message.
   * @param arg2 The second argument to format the message.
   */
  default void log(
      final @NotNull SentryLevel level,
      final @NotNull String message,
      final @Nullable Object arg1,
      final @Nullable Object arg2) {
    log(level, message, new Object[] {arg1, arg2});
  }

  /**
   * Logs a message with the specified level, message and optional arguments.
   *
   * @param level The SentryLevel.
   * @param message The message.
   * @param throwable The throwable to log.
   */
  void log(@NotNull SentryLevel level, @NotNull String message, @Nullable Throwable throwable);

  /**
   * Logs a message with the specified level, throwable, message and optional arguments.
   *
   * @param level The SentryLevel.
   * @param throwable The throwable to log.
   * @param message The message.
   * @param args the formatting arguments
   */
  void log(
      @NotNull SentryLevel level,
      @Nullable Throwable throwable,
      @NotNull String message,
      @Nullable Object... args);

  /**
   * Whether this logger is enabled for the specified SentryLevel.
   *
   * @param level The SentryLevel to test against.
   * @return True if a log message would be recorded for the level. Otherwise false.
   */
  boolean isEnabled(final @Nullable SentryLevel level);
}
