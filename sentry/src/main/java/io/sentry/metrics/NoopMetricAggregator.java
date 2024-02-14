package io.sentry.metrics;

import io.sentry.IMetricAggregator;
import io.sentry.MeasurementUnit;
import java.io.IOException;
import java.util.Calendar;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class NoopMetricAggregator implements IMetricAggregator {

  private static final NoopMetricAggregator instance = new NoopMetricAggregator();

  public static NoopMetricAggregator getInstance() {
    return instance;
  }

  @Override
  public void increment(
      @NotNull String key,
      double value,
      @Nullable MeasurementUnit unit,
      @Nullable Map<String, String> tags,
      @Nullable Calendar timestamp,
      int stackLevel) {}

  @Override
  public void gauge(
      @NotNull String key,
      double value,
      @Nullable MeasurementUnit unit,
      @Nullable Map<String, String> tags,
      @Nullable Calendar timestamp,
      int stackLevel) {}

  @Override
  public void distribution(
      @NotNull String key,
      double value,
      @Nullable MeasurementUnit unit,
      @Nullable Map<String, String> tags,
      @Nullable Calendar timestamp,
      int stackLevel) {}

  @Override
  public void set(
      @NotNull String key,
      int value,
      @Nullable MeasurementUnit unit,
      @Nullable Map<String, String> tags,
      @Nullable Calendar timestamp,
      int stackLevel) {}

  @Override
  public void set(
      @NotNull String key,
      @NotNull String value,
      @Nullable MeasurementUnit unit,
      @Nullable Map<String, String> tags,
      @Nullable Calendar timestamp,
      int stackLevel) {}

  @Override
  public void timing(
      @NotNull String key,
      @NotNull TimingCallback callback,
      MeasurementUnit.@NotNull Duration unit,
      @Nullable Map<String, String> tags,
      @Nullable Calendar timestamp,
      int stackLevel) {}

  @Override
  public void close() throws IOException {}
}
