package io.sentry.metrics;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IMetricsApi {

  void count(final @NotNull String name);

  void count(final @NotNull String name, final @Nullable Double value);

  void count(final @NotNull String name, final @Nullable String unit);

  void count(final @NotNull String name, final @Nullable Double value, final @Nullable String unit);

  void count(
      final @NotNull String name,
      final @Nullable Double value,
      final @Nullable String unit,
      final @NotNull SentryMetricsParameters params);

  void distribution(final @NotNull String name, final @Nullable Double value);

  void distribution(
      final @NotNull String name, final @Nullable Double value, final @Nullable String unit);

  void distribution(
      final @NotNull String name,
      final @Nullable Double value,
      final @Nullable String unit,
      final @NotNull SentryMetricsParameters params);

  void gauge(final @NotNull String name, final @Nullable Double value);

  void gauge(final @NotNull String name, final @Nullable Double value, final @Nullable String unit);

  void gauge(
      final @NotNull String name,
      final @Nullable Double value,
      final @Nullable String unit,
      final @NotNull SentryMetricsParameters params);
}
