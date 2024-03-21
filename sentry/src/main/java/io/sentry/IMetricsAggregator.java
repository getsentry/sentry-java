package io.sentry;

import io.sentry.metrics.LocalMetricsAggregator;
import java.io.Closeable;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IMetricsAggregator extends Closeable {

  /**
   * Emits a Counter metric
   *
   * @param key A unique key identifying the metric
   * @param value The value to be added
   * @param unit An optional unit, see {@link MeasurementUnit}
   * @param tags Optional Tags to associate with the metric
   * @param timestampMs The time when the metric was emitted. Defaults to the time at which the
   *     metric is emitted, if no value is provided.
   * @param localMetricsAggregator The local metrics aggregator for creating span summaries
   */
  void increment(
      final @NotNull String key,
      final double value,
      final @Nullable MeasurementUnit unit,
      final @Nullable Map<String, String> tags,
      final long timestampMs,
      final @Nullable LocalMetricsAggregator localMetricsAggregator);

  /**
   * Emits a Gauge metric
   *
   * @param key A unique key identifying the metric
   * @param value The value to be added
   * @param unit An optional unit, see {@link MeasurementUnit}
   * @param tags Optional Tags to associate with the metric
   * @param timestampMs The time when the metric was emitted. Defaults to the time at which the
   *     metric is emitted, if no value is provided.
   * @param localMetricsAggregator The local metrics aggregator for creating span summaries
   */
  void gauge(
      final @NotNull String key,
      final double value,
      final @Nullable MeasurementUnit unit,
      final @Nullable Map<String, String> tags,
      final long timestampMs,
      final @Nullable LocalMetricsAggregator localMetricsAggregator);

  /**
   * Emits a Distribution metric
   *
   * @param key A unique key identifying the metric
   * @param value The value to be added
   * @param unit An optional unit, see {@link MeasurementUnit}
   * @param tags Optional Tags to associate with the metric
   * @param timestampMs The time when the metric was emitted. Defaults to the time at which the
   *     metric is emitted, if no value is provided.
   * @param localMetricsAggregator The local metrics aggregator for creating span summaries
   */
  void distribution(
      final @NotNull String key,
      final double value,
      final @Nullable MeasurementUnit unit,
      final @Nullable Map<String, String> tags,
      final long timestampMs,
      final @Nullable LocalMetricsAggregator localMetricsAggregator);

  /**
   * Emits a Set metric
   *
   * @param key A unique key identifying the metric
   * @param value The value to be added
   * @param unit An optional unit, see {@link MeasurementUnit}
   * @param tags Optional Tags to associate with the metric
   * @param timestampMs The time when the metric was emitted. Defaults to the time at which the
   *     metric is emitted, if no value is provided.
   * @param localMetricsAggregator The local metrics aggregator for creating span summaries
   */
  void set(
      final @NotNull String key,
      final int value,
      final @Nullable MeasurementUnit unit,
      final @Nullable Map<String, String> tags,
      final long timestampMs,
      final @Nullable LocalMetricsAggregator localMetricsAggregator);

  /**
   * Emits a Set metric
   *
   * @param key A unique key identifying the metric
   * @param value The value to be added
   * @param unit An optional unit, see {@link MeasurementUnit}
   * @param tags Optional Tags to associate with the metric
   * @param timestampMs The time when the metric was emitted. Defaults to the time at which the
   *     metric is emitted, if no value is provided.
   * @param localMetricsAggregator The local metrics aggregator for creating span summaries
   */
  void set(
      final @NotNull String key,
      final @NotNull String value,
      final @Nullable MeasurementUnit unit,
      final @Nullable Map<String, String> tags,
      final long timestampMs,
      final @Nullable LocalMetricsAggregator localMetricsAggregator);

  /**
   * Emits a distribution with the time it takes to run a given code block.
   *
   * @param key A unique key identifying the metric
   * @param callback The code block to measure
   * @param unit An optional unit, see {@link MeasurementUnit.Duration}, defaults to seconds
   * @param tags Optional Tags to associate with the metric
   * @param localMetricsAggregator The local metrics aggregator for creating span summaries
   */
  void timing(
      final @NotNull String key,
      final @NotNull Runnable callback,
      final @NotNull MeasurementUnit.Duration unit,
      final @Nullable Map<String, String> tags,
      final @Nullable LocalMetricsAggregator localMetricsAggregator);

  void flush(boolean force);
}
