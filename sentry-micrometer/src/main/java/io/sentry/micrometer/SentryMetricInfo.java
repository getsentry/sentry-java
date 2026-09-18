package io.sentry.micrometer;

import io.sentry.SentryAttributes;
import io.sentry.metrics.SentryMetricsParameters;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class SentryMetricInfo {
  private static final @NotNull String ORIGIN = "auto.metrics.micrometer";

  private final @NotNull String name;
  private final @Nullable String unit;
  private final @NotNull SentryAttributes attributes;

  SentryMetricInfo(
      final @NotNull String name,
      final @Nullable String unit,
      final @NotNull SentryAttributes attributes) {
    this.name = name;
    this.unit = unit;
    this.attributes = attributes;
  }

  @NotNull
  String getName() {
    return name;
  }

  @Nullable
  String getUnit() {
    return unit;
  }

  @NotNull
  SentryMetricsParameters createParameters() {
    final @NotNull SentryMetricsParameters parameters = SentryMetricsParameters.create(attributes);
    parameters.setOrigin(ORIGIN);
    return parameters;
  }
}
