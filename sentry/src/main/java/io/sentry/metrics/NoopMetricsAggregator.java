package io.sentry.metrics;

import io.sentry.IMetricsAggregator;
import io.sentry.ISpan;
import io.sentry.MeasurementUnit;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class NoopMetricsAggregator
    implements IMetricsAggregator, MetricsApi.IMetricsInterface {

  private static final NoopMetricsAggregator instance = new NoopMetricsAggregator();

  public static NoopMetricsAggregator getInstance() {
    return instance;
  }

  @Override
  public void increment(
      final @NotNull String key,
      final double value,
      final @Nullable MeasurementUnit unit,
      final @Nullable Map<String, String> tags,
      final long timestampMs,
      final @Nullable LocalMetricsAggregator localMetricsAggregator) {
    // no-op
  }

  @Override
  public void gauge(
      final @NotNull String key,
      final double value,
      final @Nullable MeasurementUnit unit,
      final @Nullable Map<String, String> tags,
      final long timestampMs,
      final @Nullable LocalMetricsAggregator localMetricsAggregator) {
    // no-op
  }

  @Override
  public void distribution(
      final @NotNull String key,
      final double value,
      final @Nullable MeasurementUnit unit,
      final @Nullable Map<String, String> tags,
      final long timestampMs,
      final @Nullable LocalMetricsAggregator localMetricsAggregator) {
    // no-op
  }

  @Override
  public void set(
      final @NotNull String key,
      final int value,
      final @Nullable MeasurementUnit unit,
      final @Nullable Map<String, String> tags,
      final long timestampMs,
      final @Nullable LocalMetricsAggregator localMetricsAggregator) {
    // no-op
  }

  @Override
  public void set(
      final @NotNull String key,
      final @NotNull String value,
      final @Nullable MeasurementUnit unit,
      final @Nullable Map<String, String> tags,
      final long timestampMs,
      final @Nullable LocalMetricsAggregator localMetricsAggregator) {
    // no-op
  }

  @Override
  public void timing(
      final @NotNull String key,
      final @NotNull Runnable callback,
      final @NotNull MeasurementUnit.Duration unit,
      final @Nullable Map<String, String> tags,
      final @Nullable LocalMetricsAggregator localMetricsAggregator) {
    callback.run();
  }

  @Override
  public void flush(final boolean force) {
    // no-op
  }

  @Override
  public void close() throws IOException {}

  @Override
  public @NotNull IMetricsAggregator getMetricsAggregator() {
    return this;
  }

  @Override
  public @Nullable LocalMetricsAggregator getLocalMetricsAggregator() {
    return null;
  }

  @Override
  public @NotNull Map<String, String> getDefaultTagsForMetrics() {
    return Collections.emptyMap();
  }

  @Override
  public @Nullable ISpan startSpanForMetric(@NotNull String op, @NotNull String description) {
    return null;
  }
}
