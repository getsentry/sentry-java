package io.sentry.metrics;

import org.jetbrains.annotations.ApiStatus;

/** The metric instrument type */
@ApiStatus.Internal
public enum MetricType {
  Counter,
  Gauge,
  Distribution,
  Set
}
