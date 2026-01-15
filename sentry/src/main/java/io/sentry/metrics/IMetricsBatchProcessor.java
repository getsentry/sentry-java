package io.sentry.metrics;

import io.sentry.SentryMetricsEvent;
import org.jetbrains.annotations.NotNull;

public interface IMetricsBatchProcessor {
  void add(@NotNull SentryMetricsEvent event);

  void close(boolean isRestarting);

  /**
   * Flushes metrics.
   *
   * @param timeoutMillis time in milliseconds
   */
  void flush(long timeoutMillis);
}
