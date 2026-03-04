package io.sentry.logger;

import io.sentry.SentryClient;
import io.sentry.SentryOptions;
import org.jetbrains.annotations.NotNull;

public final class DefaultLoggerBatchProcessorFactory implements ILoggerBatchProcessorFactory {
  @Override
  public @NotNull ILoggerBatchProcessor create(
      @NotNull SentryOptions options, @NotNull SentryClient client) {
    return new LoggerBatchProcessor(options, client);
  }
}
