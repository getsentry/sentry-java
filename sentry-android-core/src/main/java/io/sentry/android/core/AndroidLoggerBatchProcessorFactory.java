package io.sentry.android.core;

import io.sentry.SentryClient;
import io.sentry.SentryOptions;
import io.sentry.logger.ILoggerBatchProcessor;
import io.sentry.logger.ILoggerBatchProcessorFactory;
import org.jetbrains.annotations.NotNull;

public final class AndroidLoggerBatchProcessorFactory implements ILoggerBatchProcessorFactory {
  @Override
  public @NotNull ILoggerBatchProcessor create(
      @NotNull SentryOptions options, @NotNull SentryClient client) {
    return new AndroidLoggerBatchProcessor(options, client);
  }
}
