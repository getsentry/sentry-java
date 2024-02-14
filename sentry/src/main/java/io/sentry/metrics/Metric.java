package io.sentry.metrics;

import io.sentry.MeasurementUnit;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Base class for metric instruments */
@ApiStatus.Internal
public abstract class Metric {

  private final @NotNull String key;
  private final @Nullable MeasurementUnit unit;
  private final @Nullable Map<String, String> tags;
  private final @NotNull Long timestampMs;

  /**
   * Creates a new instance of {@link Metric}.
   *
   * @param key The text key to be used to identify the metric
   * @param unit An optional {@link MeasurementUnit} that describes the values being tracked
   * @param tags An optional set of key/value pairs that can be used to add dimensionality to
   *     metrics
   * @param timestampMs A time when the metric was emitted.
   */
  public Metric(
      @NotNull String key,
      @Nullable MeasurementUnit unit,
      @Nullable Map<String, String> tags,
      @NotNull Long timestampMs) {
    this.key = key;
    this.unit = unit;
    this.tags = tags;
    this.timestampMs = timestampMs;
  }

  /** Adds a value to the metric */
  public abstract void add(final double value);

  public abstract MetricType getType();

  public abstract int getWeight();

  @NotNull
  public String getKey() {
    return key;
  }

  @Nullable
  public MeasurementUnit getUnit() {
    return unit;
  }

  @Nullable
  public Map<String, String> getTags() {
    return tags;
  }

  /** the unix timestamp in milliseconds when the metric was emitted. */
  public Long getTimeStampMs() {
    return timestampMs;
  }

  public abstract @NotNull Iterable<?> getValues();
}
