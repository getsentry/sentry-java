package io.sentry;

import io.sentry.metrics.CounterMetric;
import io.sentry.metrics.DistributionMetric;
import io.sentry.metrics.EncodedMetrics;
import io.sentry.metrics.GaugeMetric;
import io.sentry.metrics.IMetricsHub;
import io.sentry.metrics.Metric;
import io.sentry.metrics.MetricHelper;
import io.sentry.metrics.MetricType;
import io.sentry.metrics.SetMetric;
import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

@ApiStatus.Internal
public final class MetricAggregator implements IMetricAggregator, Runnable, Closeable {

  private final @NotNull IMetricsHub hub;
  private final @NotNull ILogger logger;
  private @NotNull TimeProvider timeProvider = System::currentTimeMillis;

  private volatile @NotNull ISentryExecutorService executorService;
  private volatile boolean isClosed = false;

  // The key for this dictionary is the Timestamp for the bucket, rounded down to the nearest
  // RollupInSeconds... so it
  // aggregates all of the metrics data for a particular time period. The Value is a dictionary for
  // the metrics,
  // each of which has a key that uniquely identifies it within the time period
  private final NavigableMap<Long, Map<String, Metric>> buckets = new ConcurrentSkipListMap<>();

  public MetricAggregator(final @NotNull IMetricsHub hub, final @NotNull ILogger logger) {
    this.hub = hub;
    this.logger = logger;
    this.executorService = NoOpSentryExecutorService.getInstance();
  }

  @Override
  public void increment(
      @NotNull String key,
      double value,
      @Nullable MeasurementUnit unit,
      @Nullable Map<String, String> tags,
      final long timestampMs,
      int stackLevel) {
    add(MetricType.Counter, key, value, unit, tags, timestampMs, stackLevel);
  }

  @Override
  public void gauge(
      @NotNull String key,
      double value,
      @Nullable MeasurementUnit unit,
      @Nullable Map<String, String> tags,
      final long timestampMs,
      int stackLevel) {
    add(MetricType.Gauge, key, value, unit, tags, timestampMs, stackLevel);
  }

  @Override
  public void distribution(
      @NotNull String key,
      double value,
      @Nullable MeasurementUnit unit,
      @Nullable Map<String, String> tags,
      final long timestampMs,
      int stackLevel) {
    add(MetricType.Distribution, key, value, unit, tags, timestampMs, stackLevel);
  }

  @Override
  public void set(
      @NotNull String key,
      int value,
      @Nullable MeasurementUnit unit,
      @Nullable Map<String, String> tags,
      final long timestampMs,
      int stackLevel) {
    add(MetricType.Set, key, value, unit, tags, timestampMs, stackLevel);
  }

  @Override
  public void set(
      @NotNull String key,
      @NotNull String value,
      @Nullable MeasurementUnit unit,
      @Nullable Map<String, String> tags,
      final long timestampMs,
      int stackLevel) {
    // TODO consider using CR32 instead of hashCode
    // see https://develop.sentry.dev/sdk/metrics/#sets
    add(MetricType.Set, key, value.hashCode(), unit, tags, timestampMs, stackLevel);
  }

  @Override
  public void timing(
      @NotNull String key,
      @NotNull TimingCallback callback,
      @NotNull MeasurementUnit.Duration unit,
      @Nullable Map<String, String> tags,
      final long timestampMs,
      int stackLevel) {
    final long start = System.nanoTime();
    try {
      callback.run();
    } finally {
      final long durationNanos = (System.nanoTime() - start);
      final double value = MetricHelper.convertNanosTo(unit, durationNanos);
      add(MetricType.Distribution, key, value, unit, tags, timestampMs, stackLevel + 1);
    }
  }

  @SuppressWarnings({"FutureReturnValueIgnored", "UnusedVariable"})
  private void add(
      final @NotNull MetricType type,
      final @NotNull String key,
      final double value,
      @Nullable MeasurementUnit unit,
      final @Nullable Map<String, String> tags,
      @Nullable Long timestampMs,
      final int stackLevel) {

    if (timestampMs == null) {
      timestampMs = timeProvider.getTimeMillis();
    }

    final @NotNull Metric metric;
    switch (type) {
      case Counter:
        metric = new CounterMetric(key, value, unit, tags, timestampMs);
        break;
      case Gauge:
        metric = new GaugeMetric(key, value, unit, tags, timestampMs);
        break;
      case Distribution:
        metric = new DistributionMetric(key, value, unit, tags, timestampMs);
        break;
      case Set:
        metric = new SetMetric(key, unit, tags, timestampMs);
        //noinspection unchecked
        metric.add((int) value);
        break;
      default:
        throw new IllegalArgumentException("Unknown MetricType: " + type.name());
    }

    final long timeBucketKey = MetricHelper.getTimeBucketKey(timestampMs);
    final @NotNull Map<String, Metric> timeBucket = getOrAddTimeBucket(timeBucketKey);

    final @NotNull String metricKey = MetricHelper.getMetricBucketKey(type, key, unit, tags);
    synchronized (timeBucket) {
      @Nullable Metric existingMetric = timeBucket.get(metricKey);
      if (existingMetric != null) {
        existingMetric.add(value);
      } else {
        timeBucket.put(metricKey, metric);
      }
    }

    // spin up real executor service the first time metrics are collected
    if (!isClosed && executorService instanceof NoOpSentryExecutorService) {
      synchronized (this) {
        if (!isClosed && executorService instanceof NoOpSentryExecutorService) {
          executorService = new SentryExecutorService();
          executorService.schedule(this, MetricHelper.FLUSHER_SLEEP_TIME_MS);
        }
      }
    }
  }

  @Override
  public void flush(final boolean force) {
    final @NotNull Set<Long> flushableBuckets = getFlushableBuckets(force);
    if (flushableBuckets.isEmpty()) {
      logger.log(SentryLevel.DEBUG, "Metrics: nothing to flush");
      return;
    }
    logger.log(SentryLevel.DEBUG, "Metrics: flushing " + flushableBuckets.size() + " buckets");

    final @NotNull StringBuilder writer = new StringBuilder();
    for (long bucketKey : flushableBuckets) {
      final @Nullable Map<String, Metric> metrics = buckets.remove(bucketKey);
      if (metrics != null) {
        MetricHelper.encodeMetrics(bucketKey, metrics.values(), writer);
      }
    }

    if (writer.length() == 0) {
      logger.log(SentryLevel.DEBUG, "Metrics: only empty buckets found");
      return;
    }

    logger.log(SentryLevel.DEBUG, "Metrics: capturing metrics");
    final @NotNull EncodedMetrics encodedMetrics = new EncodedMetrics(writer.toString());
    hub.captureMetrics(encodedMetrics);
  }

  @NotNull
  public Set<Long> getFlushableBuckets(final boolean force) {
    if (force) {
      return buckets.keySet();
    } else {
      // get all keys, including the cutoff key
      final long cutoffTimestampMs =
          MetricHelper.getCutoffTimestampMs(timeProvider.getTimeMillis());
      final long cutoffKey = MetricHelper.getTimeBucketKey(cutoffTimestampMs);
      return buckets.headMap(cutoffKey, true).keySet();
    }
  }

  @SuppressWarnings("Java8MapApi")
  @NotNull
  private Map<String, Metric> getOrAddTimeBucket(final long bucketKey) {
    @Nullable Map<String, Metric> bucket = buckets.get(bucketKey);
    if (bucket == null) {
      // although buckets is thread safe, we still need to synchronize here to avoid overwriting
      // buckets
      synchronized (buckets) {
        bucket = buckets.get(bucketKey);
        if (bucket == null) {
          bucket = new HashMap<>();
          buckets.put(bucketKey, bucket);
        }
      }
    }
    return bucket;
  }

  @Override
  public void close() throws IOException {
    synchronized (this) {
      this.isClosed = true;
      executorService.close(0);
    }
    flush(true);
  }

  @Override
  @SuppressWarnings("FutureReturnValueIgnored")
  public void run() {
    flush(false);

    if (!isClosed) {
      executorService.schedule(this, MetricHelper.FLUSHER_SLEEP_TIME_MS);
    }
  }

  @TestOnly
  void setTimeProvider(final @NotNull TimeProvider timeProvider) {
    this.timeProvider = timeProvider;
  }

  public interface TimeProvider {
    long getTimeMillis();
  }
}
