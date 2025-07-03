package io.sentry.logger;

import io.sentry.SentryLogEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public final class NoOpLoggerBatchProcessor implements ILoggerBatchProcessor {

  private static final NoOpLoggerBatchProcessor instance = new NoOpLoggerBatchProcessor();

  private NoOpLoggerBatchProcessor() {}

  public static NoOpLoggerBatchProcessor getInstance() {
    return instance;
  }

  @Override
  public void add(@NotNull SentryLogEvent event) {
    // do nothing
  }

  @Override
  public void close(final boolean isRestarting) {
    // do nothing
  }

  @Override
  public void flush(long timeoutMillis) {
    // do nothing
  }
}
