package io.sentry.logger;

import io.sentry.SentryDate;
import io.sentry.SentryLogLevel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public interface ILoggerApi {

  void trace(final @Nullable String message, @Nullable Object... args);

  void debug(final @Nullable String message, @Nullable Object... args);

  void info(final @Nullable String message, @Nullable Object... args);

  void warn(final @Nullable String message, @Nullable Object... args);

  void error(final @Nullable String message, @Nullable Object... args);

  void fatal(final @Nullable String message, @Nullable Object... args);

  void log(@NotNull SentryLogLevel level, @Nullable String message, @Nullable Object... args);

  void log(
      @NotNull SentryLogLevel level,
      @Nullable SentryDate timestamp,
      @Nullable String message,
      @Nullable Object... args);

  void log(
      @NotNull SentryLogLevel level,
      @NotNull LogParams params,
      @Nullable String message,
      @Nullable Object... args);
}
