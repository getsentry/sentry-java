package io.sentry.android.core;

import io.sentry.Scopes;
import io.sentry.logger.ILoggerApiFactory;
import io.sentry.logger.LoggerApi;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class AndroidLoggerApiFactory implements ILoggerApiFactory {

  @Override
  @NotNull
  public LoggerApi create(@NotNull Scopes scopes) {
    return new AndroidLoggerApi(scopes);
  }
}
