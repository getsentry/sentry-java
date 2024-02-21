package io.sentry.metrics;

import io.sentry.MeasurementUnit;
import java.util.Arrays;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Gauges track a value that can go up and down. */
@ApiStatus.Internal
public final class GaugeMetric extends Metric {

  private double last;
  private double min;
  private double max;
  private double sum;
  private int count;

  public GaugeMetric(
      final @NotNull String key,
      final double value,
      final @Nullable MeasurementUnit unit,
      final @Nullable Map<String, String> tags,
      final @NotNull Long timestamp) {
    super(MetricType.Gauge, key, unit, tags, timestamp);

    this.last = value;
    this.min = value;
    this.max = value;
    this.sum = value;
    this.count = 1;
  }

  @Override
  public void add(final double value) {
    this.last = value;
    min = Math.min(min, value);
    max = Math.max(max, value);
    sum += value;
    count++;
  }

  @Override
  public int getWeight() {
    return 5;
  }

  @Override
  public @NotNull Iterable<?> getValues() {
    return Arrays.asList(last, min, max, sum, count);
  }
}
