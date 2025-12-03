package io.sentry.logger;

import io.sentry.Scopes;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface ILoggerApiFactory {
  @NotNull
  LoggerApi create(@NotNull Scopes scopes);
}
