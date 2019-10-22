package io.sentry.core;

/** ILogger implementation to System.out. */
class SystemOutLogger implements ILogger {

  /**
   * Logs to console a message with the specified level, message and optional arguments.
   *
   * @param level The SentryLevel.
   * @param message The message.
   * @param args The optional arguments to format the message.
   */
  @Override
  public void log(SentryLevel level, String message, Object... args) {
    System.out.println(String.format("%s: %s", level, String.format(message, args)));
  }

  /**
   * Logs to console a message with the specified level, message and throwable.
   *
   * @param level The SentryLevel.
   * @param message The message.
   * @param throwable The throwable to log.
   */
  @Override
  public void log(SentryLevel level, String message, Throwable throwable) {
    System.out.println(
        String.format("%s: %s", level, String.format(message, throwable.toString())));
  }
}
