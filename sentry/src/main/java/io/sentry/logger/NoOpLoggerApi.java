package io.sentry.logger;

import io.sentry.SentryDate;
import io.sentry.SentryLogLevel;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public final class NoOpLoggerApi implements ILoggerApi {

  private static final NoOpLoggerApi instance = new NoOpLoggerApi();

  private NoOpLoggerApi() {}

  public static NoOpLoggerApi getInstance() {
    return instance;
  }

  @Override
  public void trace(@Nullable String message, @Nullable Object... args) {
    // do nothing
  }

  @Override
  public void debug(@Nullable String message, @Nullable Object... args) {
    // do nothing
  }

  @Override
  public void info(@Nullable String message, @Nullable Object... args) {
    // do nothing
  }

  @Override
  public void warn(@Nullable String message, @Nullable Object... args) {
    // do nothing
  }

  @Override
  public void error(@Nullable String message, @Nullable Object... args) {
    // do nothing
  }

  @Override
  public void fatal(@Nullable String message, @Nullable Object... args) {
    // do nothing
  }

  @Override
  public void log(
      @NotNull SentryLogLevel level, @Nullable String message, @Nullable Object... args) {
    // do nothing
  }

  @Override
  public void log(
      @NotNull SentryLogLevel level,
      @Nullable SentryDate timestamp,
      @Nullable String message,
      @Nullable Object... args) {
    // do nothing
  }

  @Override
  public void log(
      @Nullable Map<String, Object> attributes,
      @NotNull SentryLogLevel level,
      @Nullable String message,
      @Nullable Object... args) {
    // do nothing
  }

  @Override
  public void log(
      @Nullable Map<String, Object> attributes,
      @NotNull SentryLogLevel level,
      @Nullable SentryDate timestamp,
      @Nullable String message,
      @Nullable Object... args) {
    // do nothing
  }
}
