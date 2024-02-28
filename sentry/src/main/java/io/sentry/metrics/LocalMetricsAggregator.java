package io.sentry.metrics;

import io.sentry.MeasurementUnit;
import io.sentry.protocol.MetricSummary;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentSkipListMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Correlates metrics to spans. See <a
 * href="https://github.com/getsentry/rfcs/blob/main/text/0123-metrics-correlation.md">the RFC</a>
 * for more details.
 */
@ApiStatus.Internal
public final class LocalMetricsAggregator {

  // format: <export key, <metric key, gauge>>
  private final @NotNull Map<String, Map<String, GaugeMetric>> buckets =
      new ConcurrentSkipListMap<>();

  public void add(
      final @NotNull String bucketKey,
      final @NotNull MetricType type,
      final @NotNull String key,
      final double value,
      final @Nullable MeasurementUnit unit,
      final @Nullable Map<String, String> tags,
      final long timestampMs) {

    final @NotNull String exportKey = MetricsHelper.getExportKey(type, key, unit);

    synchronized (buckets) {
      @Nullable Map<String, GaugeMetric> bucket = buckets.get(exportKey);
      //noinspection Java8MapApi
      if (bucket == null) {
        bucket = new HashMap<>();
        buckets.put(exportKey, bucket);
      }

      @Nullable GaugeMetric gauge = bucket.get(bucketKey);
      if (gauge == null) {
        gauge = new GaugeMetric(key, value, unit, tags, timestampMs);
        bucket.put(bucketKey, gauge);
      } else {
        gauge.add(value);
      }
    }
  }

  @NotNull
  public Map<String, List<MetricSummary>> getSummaries() {
    final @NotNull Map<String, List<MetricSummary>> summaries = new HashMap<>();
    synchronized (buckets) {
      for (final @NotNull Map.Entry<String, Map<String, GaugeMetric>> entry : buckets.entrySet()) {
        final @NotNull String exportKey = Objects.requireNonNull(entry.getKey());
        final @NotNull List<MetricSummary> metricSummaries = new ArrayList<>();
        for (@NotNull GaugeMetric gauge : entry.getValue().values()) {
          metricSummaries.add(
              new MetricSummary(
                  gauge.getMin(),
                  gauge.getMax(),
                  gauge.getSum(),
                  gauge.getCount(),
                  gauge.getTags()));
        }
        summaries.put(exportKey, metricSummaries);
      }
    }
    return summaries;
  }
}
