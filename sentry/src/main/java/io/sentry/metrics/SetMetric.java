package io.sentry.metrics;

import io.sentry.MeasurementUnit;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Sets track a set of values on which you can perform aggregations such as count_unique. */
@ApiStatus.Internal
public final class SetMetric extends Metric {

  private final @NotNull Set<Integer> values;

  public SetMetric(
      final @NotNull String key,
      final @Nullable MeasurementUnit unit,
      final @Nullable Map<String, String> tags,
      final @NotNull Long timestamp) {
    super(key, unit, tags, timestamp);
    this.values = new HashSet<>();
  }

  @Override
  public void add(final double value) {
    values.add((int) value);
  }

  @Override
  public MetricType getType() {
    return MetricType.Set;
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
