package io.sentry.metrics;

import io.sentry.IMetricsAggregator;
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
      @NotNull String key,
      double value,
      @Nullable MeasurementUnit unit,
      @Nullable Map<String, String> tags,
      long timestampMs,
      int stackLevel) {}

  @Override
  public void gauge(
      @NotNull String key,
      double value,
      @Nullable MeasurementUnit unit,
      @Nullable Map<String, String> tags,
      long timestampMs,
      int stackLevel) {}

  @Override
  public void distribution(
      @NotNull String key,
      double value,
      @Nullable MeasurementUnit unit,
      @Nullable Map<String, String> tags,
      long timestampMs,
      int stackLevel) {}

  @Override
  public void set(
      @NotNull String key,
      int value,
      @Nullable MeasurementUnit unit,
      @Nullable Map<String, String> tags,
      long timestampMs,
      int stackLevel) {}

  @Override
  public void set(
      @NotNull String key,
      @NotNull String value,
      @Nullable MeasurementUnit unit,
      @Nullable Map<String, String> tags,
      long timestampMs,
      int stackLevel) {}

  @Override
  public void timing(
      @NotNull String key,
      @NotNull Runnable callback,
      @NotNull MeasurementUnit.Duration unit,
      @Nullable Map<String, String> tags,
      int stackLevel) {
    callback.run();
  }

  @Override
  public void flush(boolean force) {
    // no-op
  }

  @Override
  public void close() throws IOException {}

  @Override
  public @NotNull IMetricsAggregator getMetricsAggregator() {
    return this;
  }

  @Override
  public @NotNull Map<String, String> getDefaultTagsForMetrics() {
    return Collections.emptyMap();
  }
}
