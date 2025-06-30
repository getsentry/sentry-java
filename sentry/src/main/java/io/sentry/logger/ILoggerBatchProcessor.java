package io.sentry.logger;

import io.sentry.SentryLogEvent;
import org.jetbrains.annotations.NotNull;

public interface ILoggerBatchProcessor {
  void add(@NotNull SentryLogEvent event);

  void close(boolean isRestarting);

  /**
   * Flushes log events.
   *
   * @param timeoutMillis time in milliseconds
   */
  void flush(long timeoutMillis);
}
