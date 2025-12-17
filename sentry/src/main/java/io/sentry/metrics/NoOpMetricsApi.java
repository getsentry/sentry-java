package io.sentry.metrics;

import org.jetbrains.annotations.NotNull;

public final class NoOpMetricsApi implements IMetricsApi {
  private static final NoOpMetricsApi instance = new NoOpMetricsApi();

  private NoOpMetricsApi() {}

  public static NoOpMetricsApi getInstance() {
    return instance;
  }

  @Override
  public void count(@NotNull String name) {}
}
