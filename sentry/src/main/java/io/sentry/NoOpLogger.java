package io.sentry;

import org.jetbrains.annotations.Nullable;

/** No-op implementation of ILogger */
public final class NoOpLogger implements ILogger {

  private static final NoOpLogger instance = new NoOpLogger();

  public static NoOpLogger getInstance() {
    return instance;
  }

  private NoOpLogger() {}

  @Override
  public void log(SentryLevel level, String message, Object... args) {}

  @Override
  public void log(SentryLevel level, String message, Throwable throwable) {}

  @Override
  public void log(SentryLevel level, Throwable throwable, String message, Object... args) {}

  @Override
  public boolean isEnabled(@Nullable SentryLevel level) {
    return false;
  }
}
