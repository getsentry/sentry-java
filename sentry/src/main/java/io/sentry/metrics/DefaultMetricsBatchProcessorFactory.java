package io.sentry.metrics;

import io.sentry.SentryClient;
import io.sentry.SentryOptions;
import org.jetbrains.annotations.NotNull;

public final class DefaultMetricsBatchProcessorFactory implements IMetricsBatchProcessorFactory {
  @Override
  public @NotNull IMetricsBatchProcessor create(
      @NotNull SentryOptions options, @NotNull SentryClient client) {
    return new MetricsBatchProcessor(options, client);
  }
}
