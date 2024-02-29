package io.sentry.metrics;

import io.sentry.IMetricsAggregator;
import io.sentry.MeasurementUnit;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MetricsApi {

  @ApiStatus.Internal
  public interface IMetricsInterface {
    @NotNull
    IMetricsAggregator getMetricsAggregator();

    @Nullable
    LocalMetricsAggregator getLocalMetricsAggregator();

    @NotNull
    Map<String, String> getDefaultTagsForMetrics();
  }

  private final @NotNull MetricsApi.IMetricsInterface aggregator;

  public MetricsApi(final @NotNull MetricsApi.IMetricsInterface aggregator) {
    this.aggregator = aggregator;
  }

  /**
   * Emits an increment of 1.0 for a counter
   *
   * @param key A unique key identifying the metric
   */
  public void increment(final @NotNull String key) {
    increment(key, 1.0, null, null, null, 1);
  }

  /**
   * Emits a Counter metric
   *
   * @param key A unique key identifying the metric
   * @param value The value to be added
   */
  public void increment(final @NotNull String key, final double value) {
    increment(key, value, null, null, null, 1);
  }

  /**
   * Emits a Counter metric
   *
   * @param key A unique key identifying the metric
   * @param value The value to be added
   * @param unit An optional unit, see {@link MeasurementUnit}
   */
  public void increment(
      final @NotNull String key, final double value, final @Nullable MeasurementUnit unit) {

    increment(key, value, unit, null, null, 1);
  }

  /**
   * Emits a Counter metric
   *
   * @param key A unique key identifying the metric
   * @param value The value to be added
   * @param unit An optional unit, see {@link MeasurementUnit}
   * @param tags Optional Tags to associate with the metric
   */
  public void increment(
      final @NotNull String key,
      final double value,
      final @Nullable MeasurementUnit unit,
      final @Nullable Map<String, String> tags) {

    increment(key, value, unit, tags, null, 1);
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
   */
  public void increment(
      final @NotNull String key,
      final double value,
      final @Nullable MeasurementUnit unit,
      final @Nullable Map<String, String> tags,
      final @Nullable Long timestampMs) {

    increment(key, value, unit, tags, timestampMs, 1);
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
    final @NotNull Map<String, String> enrichedTags =
        MetricsHelper.mergeTags(tags, aggregator.getDefaultTagsForMetrics());
    final @Nullable LocalMetricsAggregator localMetricsAggregator =
        aggregator.getLocalMetricsAggregator();

    aggregator
        .getMetricsAggregator()
        .increment(key, value, unit, enrichedTags, timestamp, stackLevel, localMetricsAggregator);
  }

  /**
   * Emits a Gauge metric
   *
   * @param key A unique key identifying the metric
   * @param value The value to be added
   */
  public void gauge(final @NotNull String key, final double value) {
    gauge(key, value, null, null, null, 1);
  }

  /**
   * Emits a Gauge metric
   *
   * @param key A unique key identifying the metric
   * @param value The value to be added
   * @param unit An optional unit, see {@link MeasurementUnit}
   */
  public void gauge(
      final @NotNull String key, final double value, final @Nullable MeasurementUnit unit) {
    gauge(key, value, unit, null, null, 1);
  }

  /**
   * Emits a Gauge metric
   *
   * @param key A unique key identifying the metric
   * @param value The value to be added
   * @param unit An optional unit, see {@link MeasurementUnit}
   * @param tags Optional Tags to associate with the metric
   */
  public void gauge(
      final @NotNull String key,
      final double value,
      final @Nullable MeasurementUnit unit,
      final @Nullable Map<String, String> tags) {

    gauge(key, value, unit, tags, null, 1);
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
   */
  public void gauge(
      final @NotNull String key,
      final double value,
      final @Nullable MeasurementUnit unit,
      final @Nullable Map<String, String> tags,
      final @Nullable Long timestampMs) {

    gauge(key, value, unit, tags, timestampMs, 1);
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
    final @NotNull Map<String, String> enrichedTags =
        MetricsHelper.mergeTags(tags, aggregator.getDefaultTagsForMetrics());
    final @Nullable LocalMetricsAggregator localMetricsAggregator =
        aggregator.getLocalMetricsAggregator();

    aggregator
        .getMetricsAggregator()
        .gauge(key, value, unit, enrichedTags, timestamp, stackLevel, localMetricsAggregator);
  }

  /**
   * Emits a Distribution metric
   *
   * @param key A unique key identifying the metric
   * @param value The value to be added
   */
  public void distribution(final @NotNull String key, final double value) {
    distribution(key, value, null, null, null, 1);
  }

  /**
   * Emits a Distribution metric
   *
   * @param key A unique key identifying the metric
   * @param value The value to be added
   * @param unit An optional unit, see {@link MeasurementUnit}
   */
  public void distribution(
      final @NotNull String key, final double value, final @Nullable MeasurementUnit unit) {

    distribution(key, value, unit, null, null, 1);
  }

  /**
   * Emits a Distribution metric
   *
   * @param key A unique key identifying the metric
   * @param value The value to be added
   * @param unit An optional unit, see {@link MeasurementUnit}
   * @param tags Optional Tags to associate with the metric
   */
  public void distribution(
      final @NotNull String key,
      final double value,
      final @Nullable MeasurementUnit unit,
      final @Nullable Map<String, String> tags) {

    distribution(key, value, unit, tags, null, 1);
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
   */
  public void distribution(
      final @NotNull String key,
      final double value,
      final @Nullable MeasurementUnit unit,
      final @Nullable Map<String, String> tags,
      final @Nullable Long timestampMs) {

    distribution(key, value, unit, tags, timestampMs, 1);
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
    final @NotNull Map<String, String> enrichedTags =
        MetricsHelper.mergeTags(tags, aggregator.getDefaultTagsForMetrics());
    final @Nullable LocalMetricsAggregator localMetricsAggregator =
        aggregator.getLocalMetricsAggregator();

    aggregator
        .getMetricsAggregator()
        .distribution(
            key, value, unit, enrichedTags, timestamp, stackLevel, localMetricsAggregator);
  }

  /**
   * Emits a Set metric
   *
   * @param key A unique key identifying the metric
   * @param value The value to be added
   */
  public void set(final @NotNull String key, final int value) {
    set(key, value, null, null, null, 1);
  }

  /**
   * Emits a Set metric
   *
   * @param key A unique key identifying the metric
   * @param value The value to be added
   * @param unit An optional unit, see {@link MeasurementUnit}
   */
  public void set(
      final @NotNull String key, final int value, final @Nullable MeasurementUnit unit) {

    set(key, value, unit, null, null, 1);
  }

  /**
   * Emits a Set metric
   *
   * @param key A unique key identifying the metric
   * @param value The value to be added
   * @param unit An optional unit, see {@link MeasurementUnit}
   * @param tags Optional Tags to associate with the metric
   */
  public void set(
      final @NotNull String key,
      final int value,
      final @Nullable MeasurementUnit unit,
      final @Nullable Map<String, String> tags) {

    set(key, value, unit, tags, null, 1);
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
   */
  public void set(
      final @NotNull String key,
      final int value,
      final @Nullable MeasurementUnit unit,
      final @Nullable Map<String, String> tags,
      final @Nullable Long timestampMs) {

    set(key, value, unit, tags, timestampMs, 1);
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
    final @NotNull Map<String, String> enrichedTags =
        MetricsHelper.mergeTags(tags, aggregator.getDefaultTagsForMetrics());
    final @Nullable LocalMetricsAggregator localMetricsAggregator =
        aggregator.getLocalMetricsAggregator();

    aggregator
        .getMetricsAggregator()
        .set(key, value, unit, enrichedTags, timestamp, stackLevel, localMetricsAggregator);
  }

  /**
   * Emits a Set metric
   *
   * @param key A unique key identifying the metric
   * @param value The value to be added
   */
  public void set(final @NotNull String key, final @NotNull String value) {
    set(key, value, null, null, null, 1);
  }

  /**
   * Emits a Set metric
   *
   * @param key A unique key identifying the metric
   * @param value The value to be added
   * @param unit An optional unit, see {@link MeasurementUnit}
   */
  public void set(
      final @NotNull String key,
      final @NotNull String value,
      final @Nullable MeasurementUnit unit) {

    set(key, value, unit, null, null, 1);
  }

  /**
   * Emits a Set metric
   *
   * @param key A unique key identifying the metric
   * @param value The value to be added
   * @param unit An optional unit, see {@link MeasurementUnit}
   * @param tags Optional Tags to associate with the metric
   */
  public void set(
      final @NotNull String key,
      final @NotNull String value,
      final @Nullable MeasurementUnit unit,
      final @Nullable Map<String, String> tags) {

    set(key, value, unit, tags, null, 1);
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
   */
  public void set(
      final @NotNull String key,
      final @NotNull String value,
      final @Nullable MeasurementUnit unit,
      final @Nullable Map<String, String> tags,
      final @Nullable Long timestampMs) {

    set(key, value, unit, tags, timestampMs, 1);
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
    final @NotNull Map<String, String> enrichedTags =
        MetricsHelper.mergeTags(tags, aggregator.getDefaultTagsForMetrics());
    final @Nullable LocalMetricsAggregator localMetricsAggregator =
        aggregator.getLocalMetricsAggregator();

    aggregator
        .getMetricsAggregator()
        .set(key, value, unit, enrichedTags, timestamp, stackLevel, localMetricsAggregator);
  }

  /**
   * Emits a distribution with the time it takes to run a given code block.
   *
   * @param key A unique key identifying the metric
   * @param callback The code block to measure
   */
  public void timing(final @NotNull String key, final @NotNull Runnable callback) {

    timing(key, callback, null, null, 1);
  }

  /**
   * Emits a distribution with the time it takes to run a given code block.
   *
   * @param key A unique key identifying the metric
   * @param callback The code block to measure
   * @param unit An optional unit, see {@link MeasurementUnit.Duration}
   */
  public void timing(
      final @NotNull String key,
      final @NotNull Runnable callback,
      final @NotNull MeasurementUnit.Duration unit) {

    timing(key, callback, unit, null, 1);
  }

  /**
   * Emits a distribution with the time it takes to run a given code block.
   *
   * @param key A unique key identifying the metric
   * @param callback The code block to measure
   * @param unit An optional unit, see {@link MeasurementUnit.Duration}
   * @param tags Optional Tags to associate with the metric
   */
  public void timing(
      final @NotNull String key,
      final @NotNull Runnable callback,
      final @NotNull MeasurementUnit.Duration unit,
      final @Nullable Map<String, String> tags) {

    timing(key, callback, unit, tags, 1);
  }

  /**
   * Emits a distribution with the time it takes to run a given code block.
   *
   * @param key A unique key identifying the metric
   * @param callback The code block to measure
   * @param unit An optional unit, see {@link MeasurementUnit.Duration}
   * @param tags Optional Tags to associate with the metric
   * @param stackLevel Optional number of stacks levels to ignore when determining the code location
   */
  public void timing(
      final @NotNull String key,
      final @NotNull Runnable callback,
      final @Nullable MeasurementUnit.Duration unit,
      final @Nullable Map<String, String> tags,
      final int stackLevel) {

    final @NotNull MeasurementUnit.Duration durationUnit =
        unit != null ? unit : MeasurementUnit.Duration.SECOND;
    final @NotNull Map<String, String> enrichedTags =
        MetricsHelper.mergeTags(tags, aggregator.getDefaultTagsForMetrics());
    final @Nullable LocalMetricsAggregator localMetricsAggregator =
        aggregator.getLocalMetricsAggregator();

    aggregator
        .getMetricsAggregator()
        .timing(key, callback, durationUnit, enrichedTags, stackLevel, localMetricsAggregator);
  }
}
