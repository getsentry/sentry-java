package io.sentry.metrics;

import io.sentry.logger.SentryLogParameters;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NoOpMetricsApi implements IMetricsApi {
  private static final NoOpMetricsApi instance = new NoOpMetricsApi();

  private NoOpMetricsApi() {}

  public static NoOpMetricsApi getInstance() {
    return instance;
  }

  @Override
  public void count(final @NotNull String name) {}

  @Override
  public void count(final @NotNull String name, final @Nullable Double value) {}

  @Override
  public void count(final @NotNull String name, final @Nullable String unit) {}

  @Override
  public void count(
      final @NotNull String name, final @Nullable Double value, final @Nullable String unit) {}

  @Override
  public void count(
      final @NotNull String name,
      final @Nullable Double value,
      final @Nullable String unit,
      final @NotNull SentryLogParameters params) {}
}
