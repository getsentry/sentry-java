package io.sentry.metrics;

import io.sentry.IMetricAggregator;
import io.sentry.MeasurementUnit;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// TODO: add tons of method overloads to make it delightful to use
public final class MetricsApi {

  private final @NotNull IMetricAggregator aggregator;

  public MetricsApi(final @NotNull IMetricAggregator aggregator) {
    this.aggregator = aggregator;
  }

  /**
   * Emits a Counter metric
   *
   * @param key A unique key identifying the metric
   * @param value The value to be added
   * @param unit An optional unit, see {@link MeasurementUnit}
   * @param tags Optional Tags to associate with the metric
   * @param timestampMs The time when the metric was emitted. Defaults to the time at which the
   *     metric is emitted, if no value is provided.
   * @param stackLevel Optional number of stacks levels to ignore when determining the code location
   */
  public void increment(
      final @NotNull String key,
      final double value,
      final @Nullable MeasurementUnit unit,
      final @Nullable Map<String, String> tags,
      final long timestampMs,
      final int stackLevel) {
    aggregator.increment(key, value, unit, tags, timestampMs, stackLevel);
  }

  /**
   * Emits a Gauge metric
   *
   * @param key A unique key identifying the metric
   * @param value The value to be added
   * @param unit An optional unit, see {@link MeasurementUnit}
   * @param tags Optional Tags to associate with the metric
   * @param timestampMs The time when the metric was emitted. Defaults to the time at which the
   *     metric is emitted, if no value is provided.
   * @param stackLevel Optional number of stacks levels to ignore when determining the code location
   */
  public void gauge(
      final @NotNull String key,
      final double value,
      final @Nullable MeasurementUnit unit,
      final @Nullable Map<String, String> tags,
      final long timestampMs,
      final int stackLevel) {
    aggregator.gauge(key, value, unit, tags, timestampMs, stackLevel);
  }

  /**
   * Emits a Distribution metric
   *
   * @param key A unique key identifying the metric
   * @param value The value to be added
   * @param unit An optional unit, see {@link MeasurementUnit}
   * @param tags Optional Tags to associate with the metric
   * @param timestampMs The time when the metric was emitted. Defaults to the time at which the
   *     metric is emitted, if no value is provided.
   * @param stackLevel Optional number of stacks levels to ignore when determining the code location
   */
  public void distribution(
      final @NotNull String key,
      final double value,
      final @Nullable MeasurementUnit unit,
      final @Nullable Map<String, String> tags,
      final long timestampMs,
      final int stackLevel) {
    aggregator.distribution(key, value, unit, tags, timestampMs, stackLevel);
  }

  /**
   * Emits a Set metric
   *
   * @param key A unique key identifying the metric
   * @param value The value to be added
   * @param unit An optional unit, see {@link MeasurementUnit}
   * @param tags Optional Tags to associate with the metric
   * @param timestampMs The time when the metric was emitted. Defaults to the time at which the
   *     metric is emitted, if no value is provided.
   * @param stackLevel Optional number of stacks levels to ignore when determining the code location
   */
  public void set(
      final @NotNull String key,
      final int value,
      final @Nullable MeasurementUnit unit,
      final @Nullable Map<String, String> tags,
      final long timestampMs,
      final int stackLevel) {
    aggregator.set(key, value, unit, tags, timestampMs, stackLevel);
  }

  /**
   * Emits a Set metric
   *
   * @param key A unique key identifying the metric
   * @param value The value to be added
   * @param unit An optional unit, see {@link MeasurementUnit}
   * @param tags Optional Tags to associate with the metric
   * @param timestampMs The time when the metric was emitted. Defaults to the time at which the
   *     metric is emitted, if no value is provided.
   * @param stackLevel Optional number of stacks levels to ignore when determining the code location
   */
  public void set(
      final @NotNull String key,
      final @NotNull String value,
      final @Nullable MeasurementUnit unit,
      final @Nullable Map<String, String> tags,
      final long timestampMs,
      final int stackLevel) {
    aggregator.set(key, value, unit, tags, timestampMs, stackLevel);
  }

  /**
   * Emits a distribution with the time it takes to run a given code block.
   *
   * @param key A unique key identifying the metric
   * @param callback The code block to measure
   * @param unit An optional unit, see {@link MeasurementUnit.Duration}
   * @param tags Optional Tags to associate with the metric
   * @param timestampMs The time when the metric was emitted
   * @param stackLevel Optional number of stacks levels to ignore when determining the code location
   */
  public void timing(
      final @NotNull String key,
      final @NotNull IMetricAggregator.TimingCallback callback,
      final @NotNull MeasurementUnit.Duration unit,
      final @Nullable Map<String, String> tags,
      final long timestampMs,
      final int stackLevel) {
    aggregator.timing(key, callback, unit, tags, timestampMs, stackLevel);
  }
}
