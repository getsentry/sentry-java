package io.sentry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** No-op implementation of ILogger */
public final class NoOpLogger implements ILogger {

  private static final NoOpLogger instance = new NoOpLogger();

  public static NoOpLogger getInstance() {
    return instance;
  }

  private NoOpLogger() {}

  @Override
  public void log(@NotNull SentryLevel level, @NotNull String message, @Nullable Object... args) {}

  @Override
  public void log(
      @NotNull SentryLevel level, @NotNull String message, @Nullable Throwable throwable) {}

  @Override
  public void log(
      @NotNull SentryLevel level,
      @Nullable Throwable throwable,
      @NotNull String message,
      @Nullable Object... args) {}

  @Override
  public boolean isEnabled(@Nullable SentryLevel level) {
    return false;
  }
}
