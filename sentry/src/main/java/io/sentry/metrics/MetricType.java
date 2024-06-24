package io.sentry.metrics;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** The metric instrument type */
@ApiStatus.Internal
public enum MetricType {
  Counter("c"),
  Gauge("g"),
  Distribution("d"),
  Set("s");

  final @NotNull String statsdCode;

  MetricType(final @NotNull String statsdCode) {
    this.statsdCode = statsdCode;
  }
}
