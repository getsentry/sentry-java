package io.sentry.metrics;

import io.sentry.MeasurementUnit;
import java.util.Calendar;
import java.util.Collections;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Counters track a value that can only be incremented. */
@ApiStatus.Internal
public final class CounterMetric extends Metric {
  private double value;

  public CounterMetric(
      final @NotNull String key,
      final double value,
      final @Nullable MeasurementUnit unit,
      final @Nullable Map<String, String> tags,
      final @NotNull Calendar timestamp) {
    super(key, unit, tags, timestamp);
    this.value = value;
  }

  public double getValue() {
    return value;
  }

  @Override
  public void add(final double value) {
    this.value += value;
  }

  @Override
  public MetricType getType() {
    return MetricType.Counter;
  }

  @Override
  public int getWeight() {
    return 1;
  }

  @Override
  public @NotNull Iterable<?> getValues() {
    return Collections.singletonList(value);
  }
}
