package io.sentry.metrics;

import io.sentry.MeasurementUnit;
import io.sentry.util.Objects;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Uniquely identifies a metric resource. Used for caching the {@link CodeLocations} for a given
 * metric.
 */
@ApiStatus.Internal
public final class MetricResourceIdentifier {
  private final @NotNull MetricType metricType;
  private final @NotNull String key;
  private final @Nullable MeasurementUnit unit;

  public MetricResourceIdentifier(
      final @NotNull MetricType metricType,
      final @NotNull String key,
      final @Nullable MeasurementUnit unit) {
    this.metricType = metricType;
    this.key = key;
    this.unit = unit;
  }

  @NotNull
  public MetricType getMetricType() {
    return metricType;
  }

  @NotNull
  public String getKey() {
    return key;
  }

  @Nullable
  public MeasurementUnit getUnit() {
    return unit;
  }

  /** Returns a string representation of the metric resource identifier. */
  @Override
  public String toString() {
    return String.format(
        "%s:%s@%s", MetricsHelper.toStatsdType(metricType), MetricsHelper.sanitizeKey(key), unit);
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final @NotNull MetricResourceIdentifier that = (MetricResourceIdentifier) o;
    return metricType == that.metricType
        && Objects.equals(key, that.key)
        && Objects.equals(unit, that.unit);
  }

  @Override
  public int hashCode() {
    return Objects.hash(metricType, key, unit);
  }
}
