package io.sentry.android.core;

import io.sentry.SentryClient;
import io.sentry.SentryOptions;
import io.sentry.metrics.IMetricsBatchProcessor;
import io.sentry.metrics.IMetricsBatchProcessorFactory;
import org.jetbrains.annotations.NotNull;

public final class AndroidMetricsBatchProcessorFactory implements IMetricsBatchProcessorFactory {
  @Override
  public @NotNull IMetricsBatchProcessor create(
      final @NotNull SentryOptions options, final @NotNull SentryClient client) {
    return new AndroidMetricsBatchProcessor(options, client);
  }
}
