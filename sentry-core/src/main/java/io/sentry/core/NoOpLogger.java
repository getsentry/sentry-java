package io.sentry.core;

/** No-op implementation of ILogger */
final class NoOpLogger implements ILogger {

  private static final NoOpLogger instance = new NoOpLogger();

  public static NoOpLogger getInstance() {
    return instance;
  }

  NoOpLogger() {}

  @Override
  public void log(SentryLevel level, String message, Object... args) {}

  @Override
  public void log(SentryLevel level, String message, Throwable throwable) {}
}
