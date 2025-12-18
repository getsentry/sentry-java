package io.sentry.metrics;

import io.sentry.logger.SentryLogParameters;
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
      final @NotNull SentryLogParameters params);

  // distribution
  // gauge
  //
}
