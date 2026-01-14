package io.sentry.metrics;

import org.jetbrains.annotations.NotNull;

public interface IMetricsApi {

  void count(@NotNull final String name);
  // distribution
  // gauge
  //
}
