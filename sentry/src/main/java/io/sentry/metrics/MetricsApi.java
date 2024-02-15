package io.sentry.metrics;

import io.sentry.IMetricsAggregator;
import io.sentry.MeasurementUnit;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// TODO: add tons of method overloads to make it delightful to use
public final class MetricsApi {

  private final @NotNull IMetricsAggregator aggregator;

  public MetricsApi(final @NotNull IMetricsAggregator aggregator) {
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
      final @Nullable Long timestampMs,
      final int stackLevel) {

    final long timestamp = timestampMs != null ? timestampMs : System.currentTimeMillis();
    aggregator.increment(key, value, unit, tags, timestamp, stackLevel);
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
      final @Nullable Long timestampMs,
      final int stackLevel) {

    final long timestamp = timestampMs != null ? timestampMs : System.currentTimeMillis();
    aggregator.gauge(key, value, unit, tags, timestamp, stackLevel);
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
      final @Nullable Long timestampMs,
      final int stackLevel) {

    final long timestamp = timestampMs != null ? timestampMs : System.currentTimeMillis();
    aggregator.distribution(key, value, unit, tags, timestamp, stackLevel);
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
      final @Nullable Long timestampMs,
      final int stackLevel) {

    final long timestamp = timestampMs != null ? timestampMs : System.currentTimeMillis();
    aggregator.set(key, value, unit, tags, timestamp, stackLevel);
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
      final @Nullable Long timestampMs,
      final int stackLevel) {

    final long timestamp = timestampMs != null ? timestampMs : System.currentTimeMillis();
    aggregator.set(key, value, unit, tags, timestamp, stackLevel);
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
      final @NotNull IMetricsAggregator.TimingCallback callback,
      final @NotNull MeasurementUnit.Duration unit,
      final @Nullable Map<String, String> tags,
      final @Nullable Long timestampMs,
      final int stackLevel) {

    final long timestamp = timestampMs != null ? timestampMs : System.currentTimeMillis();
    aggregator.timing(key, callback, unit, tags, timestamp, stackLevel);
  }
}
