package io.sentry;

import io.sentry.metrics.CounterMetric;
import io.sentry.metrics.DistributionMetric;
import io.sentry.metrics.EncodedMetrics;
import io.sentry.metrics.GaugeMetric;
import io.sentry.metrics.IMetricsHub;
import io.sentry.metrics.Metric;
import io.sentry.metrics.MetricType;
import io.sentry.metrics.MetricsHelper;
import io.sentry.metrics.SetMetric;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.zip.CRC32;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

@ApiStatus.Internal
public final class MetricsAggregator implements IMetricsAggregator, Runnable, Closeable {

  @SuppressWarnings({"CharsetObjectCanBeUsed"})
  private static final Charset UTF8 = Charset.forName("UTF-8");

  private final @NotNull IMetricsHub hub;
  private final @NotNull ILogger logger;

  private @NotNull TimeProvider timeProvider = System::currentTimeMillis;

  private volatile @NotNull ISentryExecutorService executorService;
  private volatile boolean isClosed = false;
  private volatile boolean flushScheduled = false;

  // The key for this dictionary is the Timestamp for the bucket, rounded down to the nearest
  // RollupInSeconds... so it
  // aggregates all of the metrics data for a particular time period. The Value is a dictionary for
  // the metrics,
  // each of which has a key that uniquely identifies it within the time period
  private final NavigableMap<Long, Map<String, Metric>> buckets = new ConcurrentSkipListMap<>();

  public MetricsAggregator(final @NotNull IMetricsHub hub, final @NotNull SentryOptions options) {
    this(hub, options, NoOpSentryExecutorService.getInstance());
  }

  @TestOnly
  public MetricsAggregator(
      final @NotNull IMetricsHub hub,
      final @NotNull SentryOptions options,
      final @NotNull ISentryExecutorService executorService) {
    this.hub = hub;
    this.logger = options.getLogger();
    this.executorService = executorService;
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

    final byte[] bytes = value.getBytes(UTF8);

    final CRC32 crc = new CRC32();
    crc.update(bytes, 0, bytes.length);
    final int intValue = (int) crc.getValue();

    add(MetricType.Set, key, intValue, unit, tags, timestampMs, stackLevel);
  }

  @Override
  public void timing(
      @NotNull String key,
      @NotNull TimingCallback callback,
      @NotNull MeasurementUnit.Duration unit,
      @Nullable Map<String, String> tags,
      int stackLevel) {
    final long startMs = timeProvider.getTimeMillis();
    final long startNanos = System.nanoTime();
    try {
      callback.run();
    } finally {
      final long durationNanos = (System.nanoTime() - startNanos);
      final double value = MetricsHelper.convertNanosTo(unit, durationNanos);
      add(MetricType.Distribution, key, value, unit, tags, startMs, stackLevel + 1);
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

    if (isClosed) {
      return;
    }

    if (timestampMs == null) {
      timestampMs = timeProvider.getTimeMillis();
    }

    final @NotNull Map<String, String> enrichedTags = enrichTags(tags);

    final @NotNull Metric metric;
    switch (type) {
      case Counter:
        metric = new CounterMetric(key, value, unit, enrichedTags, timestampMs);
        break;
      case Gauge:
        metric = new GaugeMetric(key, value, unit, enrichedTags, timestampMs);
        break;
      case Distribution:
        metric = new DistributionMetric(key, value, unit, enrichedTags, timestampMs);
        break;
      case Set:
        metric = new SetMetric(key, unit, enrichedTags, timestampMs);
        //noinspection unchecked
        metric.add((int) value);
        break;
      default:
        throw new IllegalArgumentException("Unknown MetricType: " + type.name());
    }

    final long timeBucketKey = MetricsHelper.getTimeBucketKey(timestampMs);
    final @NotNull Map<String, Metric> timeBucket = getOrAddTimeBucket(timeBucketKey);

    final @NotNull String metricKey =
        MetricsHelper.getMetricBucketKey(type, key, unit, enrichedTags);

    // TODO check if we can synchronize only the metric itself
    synchronized (timeBucket) {
      @Nullable Metric existingMetric = timeBucket.get(metricKey);
      if (existingMetric != null) {
        existingMetric.add(value);
      } else {
        timeBucket.put(metricKey, metric);
      }
    }

    // spin up real executor service the first time metrics are collected
    if (!isClosed && !flushScheduled) {
      synchronized (this) {
        if (!isClosed && !flushScheduled) {
          flushScheduled = true;
          // TODO this is probably not a good idea after all
          // as it will slow down the first metric emission
          // maybe move to constructor?
          if (executorService instanceof NoOpSentryExecutorService) {
            executorService = new SentryExecutorService();
          }
          executorService.schedule(this, MetricsHelper.FLUSHER_SLEEP_TIME_MS);
        }
      }
    }
  }

  @NotNull
  private Map<String, String> enrichTags(final @Nullable Map<String, String> tags) {
    final @NotNull Map<String, String> defaultTags = hub.getDefaultTagsForMetric();
    if (tags == null) {
      return Collections.unmodifiableMap(defaultTags);
    }

    final @NotNull Map<String, String> enrichedTags = new HashMap<>(tags);
    for (final @NotNull Map.Entry<String, String> defaultTag : defaultTags.entrySet()) {
      final @NotNull String key = defaultTag.getKey();
      if (!enrichedTags.containsKey(key)) {
        enrichedTags.put(key, defaultTag.getValue());
      }
    }
    return enrichedTags;
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
        MetricsHelper.encodeMetrics(bucketKey, metrics.values(), writer);
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
  private Set<Long> getFlushableBuckets(final boolean force) {
    if (force) {
      return buckets.keySet();
    } else {
      // get all keys, including the cutoff key
      final long cutoffTimestampMs =
          MetricsHelper.getCutoffTimestampMs(timeProvider.getTimeMillis());
      final long cutoffKey = MetricsHelper.getTimeBucketKey(cutoffTimestampMs);
      return buckets.headMap(cutoffKey, true).keySet();
    }
  }

  @SuppressWarnings("Java8MapApi")
  @NotNull
  private Map<String, Metric> getOrAddTimeBucket(final long bucketKey) {
    @Nullable Map<String, Metric> bucket = buckets.get(bucketKey);
    if (bucket == null) {
      // although buckets is thread safe, we still need to synchronize here to avoid creating
      // the same bucket at the same time
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
      isClosed = true;
      executorService.close(0);
    }
    flush(true);
  }

  @Override
  @SuppressWarnings("FutureReturnValueIgnored")
  public void run() {
    flush(false);

    synchronized (this) {
      if (!isClosed) {
        executorService.schedule(this, MetricsHelper.FLUSHER_SLEEP_TIME_MS);
      }
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
