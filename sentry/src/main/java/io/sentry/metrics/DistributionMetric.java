package io.sentry.metrics;

import io.sentry.MeasurementUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class DistributionMetric extends Metric {

  private final List<Double> values;

  public DistributionMetric(
      final @NotNull String key,
      final double value,
      final @Nullable MeasurementUnit unit,
      final @Nullable Map<String, String> tags,
      final @NotNull Long timestamp) {
    super(key, unit, tags, timestamp);
    this.values = new ArrayList<>();
    this.values.add(value);
  }

  @Override
  public void add(final double value) {
    values.add(value);
  }

  @Override
  public MetricType getType() {
    return MetricType.Distribution;
  }

  @Override
  public int getWeight() {
    return values.size();
  }

  @Override
  public @NotNull Iterable<?> getValues() {
    return values;
  }
}
