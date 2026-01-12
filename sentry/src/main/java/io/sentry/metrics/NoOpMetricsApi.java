package io.sentry.metrics;

import io.sentry.MeasurementUnit;
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
  public void count(final @NotNull String name, final @Nullable MeasurementUnit unit) {}

  @Override
  public void count(
      final @NotNull String name,
      final @Nullable Double value,
      final @Nullable MeasurementUnit unit) {}

  @Override
  public void count(
      final @NotNull String name,
      final @Nullable Double value,
      final @Nullable MeasurementUnit unit,
      final @NotNull SentryMetricsParameters params) {}

  @Override
  public void distribution(final @NotNull String name, final @Nullable Double value) {}

  @Override
  public void distribution(
      final @NotNull String name,
      final @Nullable Double value,
      final @Nullable MeasurementUnit unit) {}

  @Override
  public void distribution(
      final @NotNull String name,
      final @Nullable Double value,
      final @Nullable MeasurementUnit unit,
      final @NotNull SentryMetricsParameters params) {}

  @Override
  public void gauge(final @NotNull String name, final @Nullable Double value) {}

  @Override
  public void gauge(
      final @NotNull String name,
      final @Nullable Double value,
      final @Nullable MeasurementUnit unit) {}

  @Override
  public void gauge(
      final @NotNull String name,
      final @Nullable Double value,
      final @Nullable MeasurementUnit unit,
      final @NotNull SentryMetricsParameters params) {}
}
