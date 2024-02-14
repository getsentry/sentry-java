package io.sentry;

import java.io.Closeable;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IMetricAggregator extends Closeable {

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
  void increment(
      final @NotNull String key,
      final double value,
      final @Nullable MeasurementUnit unit,
      final @Nullable Map<String, String> tags,
      final long timestampMs,
      final int stackLevel);

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
  void gauge(
      final @NotNull String key,
      final double value,
      final @Nullable MeasurementUnit unit,
      final @Nullable Map<String, String> tags,
      final long timestampMs,
      final int stackLevel);

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
  void distribution(
      final @NotNull String key,
      final double value,
      final @Nullable MeasurementUnit unit,
      final @Nullable Map<String, String> tags,
      final long timestampMs,
      final int stackLevel);

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
  void set(
      final @NotNull String key,
      final int value,
      final @Nullable MeasurementUnit unit,
      final @Nullable Map<String, String> tags,
      final long timestampMs,
      final int stackLevel);

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
  void set(
      final @NotNull String key,
      final @NotNull String value,
      final @Nullable MeasurementUnit unit,
      final @Nullable Map<String, String> tags,
      final long timestampMs,
      final int stackLevel);

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
  void timing(
      final @NotNull String key,
      final @NotNull TimingCallback callback,
      final @NotNull MeasurementUnit.Duration unit,
      final @Nullable Map<String, String> tags,
      final long timestampMs,
      final int stackLevel);

  void flush(boolean force);

  interface TimingCallback {
    void run();
  }
}
