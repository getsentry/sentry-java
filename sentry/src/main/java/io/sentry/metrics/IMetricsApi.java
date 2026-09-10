package io.sentry.metrics;

import io.sentry.MeasurementUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IMetricsApi {

  void count(final @NotNull String name);

  void count(final @NotNull String name, final @Nullable Double value);

  void count(final @NotNull String name, final @Nullable String unit);

  default void count(final @NotNull String name, final @NotNull MeasurementUnit unit) {
    count(name, unit.apiName());
  }

  void count(final @NotNull String name, final @Nullable Double value, final @Nullable String unit);

  default void count(
      final @NotNull String name,
      final @Nullable Double value,
      final @NotNull MeasurementUnit unit) {
    count(name, value, unit.apiName());
  }

  void count(
      final @NotNull String name,
      final @Nullable Double value,
      final @Nullable String unit,
      final @NotNull SentryMetricsParameters params);

  default void count(
      final @NotNull String name,
      final @Nullable Double value,
      final @NotNull MeasurementUnit unit,
      final @NotNull SentryMetricsParameters params) {
    count(name, value, unit.apiName(), params);
  }

  void distribution(final @NotNull String name, final @Nullable Double value);

  void distribution(
      final @NotNull String name, final @Nullable Double value, final @Nullable String unit);

  default void distribution(
      final @NotNull String name,
      final @Nullable Double value,
      final @NotNull MeasurementUnit unit) {
    distribution(name, value, unit.apiName());
  }

  void distribution(
      final @NotNull String name,
      final @Nullable Double value,
      final @Nullable String unit,
      final @NotNull SentryMetricsParameters params);

  default void distribution(
      final @NotNull String name,
      final @Nullable Double value,
      final @NotNull MeasurementUnit unit,
      final @NotNull SentryMetricsParameters params) {
    distribution(name, value, unit.apiName(), params);
  }

  void gauge(final @NotNull String name, final @Nullable Double value);

  void gauge(final @NotNull String name, final @Nullable Double value, final @Nullable String unit);

  default void gauge(
      final @NotNull String name,
      final @Nullable Double value,
      final @NotNull MeasurementUnit unit) {
    gauge(name, value, unit.apiName());
  }

  void gauge(
      final @NotNull String name,
      final @Nullable Double value,
      final @Nullable String unit,
      final @NotNull SentryMetricsParameters params);

  default void gauge(
      final @NotNull String name,
      final @Nullable Double value,
      final @NotNull MeasurementUnit unit,
      final @NotNull SentryMetricsParameters params) {
    gauge(name, value, unit.apiName(), params);
  }
}
