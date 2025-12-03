package io.sentry.logger;

import io.sentry.Scopes;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class DefaultLoggerApiFactory implements ILoggerApiFactory {

  @Override
  @NotNull
  public LoggerApi create(@NotNull final Scopes scopes) {
    return new LoggerApi(scopes);
  }
}
