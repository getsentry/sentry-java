package io.sentry;

import io.sentry.metrics.CounterMetric;
import io.sentry.metrics.DistributionMetric;
import io.sentry.metrics.EncodedMetrics;
import io.sentry.metrics.GaugeMetric;
import io.sentry.metrics.IMetricsClient;
import io.sentry.metrics.LocalMetricsAggregator;
import io.sentry.metrics.Metric;
import io.sentry.metrics.MetricType;
import io.sentry.metrics.MetricsHelper;
import io.sentry.metrics.SetMetric;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

@ApiStatus.Internal
public final class MetricsAggregator implements IMetricsAggregator, Runnable, Closeable {

  @SuppressWarnings({"CharsetObjectCanBeUsed"})
  private static final Charset UTF8 = Charset.forName("UTF-8");

  private final @NotNull ILogger logger;
  private final @NotNull IMetricsClient client;
  private final @NotNull SentryDateProvider dateProvider;
  private final @Nullable SentryOptions.BeforeEmitMetricCallback beforeEmitCallback;

  private volatile @NotNull ISentryExecutorService executorService;
  private volatile boolean isClosed = false;
  private volatile boolean flushScheduled = false;

  // The key for this dictionary is the Timestamp for the bucket, rounded down to the nearest
  // RollupInSeconds... so it
  // aggregates all of the metrics data for a particular time period. The Value is a dictionary for
  // the metrics,
  // each of which has a key that uniquely identifies it within the time period
  private final @NotNull NavigableMap<Long, Map<String, Metric>> buckets =
      new ConcurrentSkipListMap<>();

  private final @NotNull AtomicInteger totalBucketsWeight = new AtomicInteger();
  private final int maxWeight;

  public MetricsAggregator(
      final @NotNull SentryOptions options, final @NotNull IMetricsClient client) {
    this(
        client,
        options.getLogger(),
        options.getDateProvider(),
        MetricsHelper.MAX_TOTAL_WEIGHT,
        options.getBeforeEmitMetricCallback(),
        NoOpSentryExecutorService.getInstance());
  }

  @TestOnly
  public MetricsAggregator(
      final @NotNull IMetricsClient client,
      final @NotNull ILogger logger,
      final @NotNull SentryDateProvider dateProvider,
      final int maxWeight,
      final @Nullable SentryOptions.BeforeEmitMetricCallback beforeEmitCallback,
      final @NotNull ISentryExecutorService executorService) {
    this.client = client;
    this.logger = logger;
    this.dateProvider = dateProvider;
    this.maxWeight = maxWeight;
    this.beforeEmitCallback = beforeEmitCallback;
    this.executorService = executorService;
  }

  @Override
  public void increment(
      @NotNull String key,
      double value,
      @Nullable MeasurementUnit unit,
      @Nullable Map<String, String> tags,
      final long timestampMs,
      int stackLevel,
      @Nullable LocalMetricsAggregator localMetricsAggregator) {
    add(
        MetricType.Counter,
        key,
        value,
        unit,
        tags,
        timestampMs,
        stackLevel,
        localMetricsAggregator);
  }

  @Override
  public void gauge(
      @NotNull String key,
      double value,
      @Nullable MeasurementUnit unit,
      @Nullable Map<String, String> tags,
      final long timestampMs,
      int stackLevel,
      @Nullable LocalMetricsAggregator localMetricsAggregator) {
    add(MetricType.Gauge, key, value, unit, tags, timestampMs, stackLevel, localMetricsAggregator);
  }

  @Override
  public void distribution(
      @NotNull String key,
      double value,
      @Nullable MeasurementUnit unit,
      @Nullable Map<String, String> tags,
      final long timestampMs,
      int stackLevel,
      @Nullable LocalMetricsAggregator localMetricsAggregator) {
    add(
        MetricType.Distribution,
        key,
        value,
        unit,
        tags,
        timestampMs,
        stackLevel,
        localMetricsAggregator);
  }

  @Override
  public void set(
      @NotNull String key,
      int value,
      @Nullable MeasurementUnit unit,
      @Nullable Map<String, String> tags,
      final long timestampMs,
      int stackLevel,
      @Nullable LocalMetricsAggregator localMetricsAggregator) {
    add(MetricType.Set, key, value, unit, tags, timestampMs, stackLevel, localMetricsAggregator);
  }

  @Override
  public void set(
      @NotNull String key,
      @NotNull String value,
      @Nullable MeasurementUnit unit,
      @Nullable Map<String, String> tags,
      final long timestampMs,
      int stackLevel,
      @Nullable LocalMetricsAggregator localMetricsAggregator) {

    final byte[] bytes = value.getBytes(UTF8);

    final CRC32 crc = new CRC32();
    crc.update(bytes, 0, bytes.length);
    final int intValue = (int) crc.getValue();

    add(MetricType.Set, key, intValue, unit, tags, timestampMs, stackLevel, localMetricsAggregator);
  }

  @Override
  public void timing(
      @NotNull String key,
      @NotNull Runnable callback,
      @NotNull MeasurementUnit.Duration unit,
      @Nullable Map<String, String> tags,
      int stackLevel,
      @Nullable LocalMetricsAggregator localMetricsAggregator) {
    final long startMs = nowMillis();
    final long startNanos = System.nanoTime();
    try {
      callback.run();
    } finally {
      final long durationNanos = (System.nanoTime() - startNanos);
      final double value = MetricsHelper.convertNanosTo(unit, durationNanos);
      add(
          MetricType.Distribution,
          key,
          value,
          unit,
          tags,
          startMs,
          stackLevel + 1,
          localMetricsAggregator);
    }
  }

  @SuppressWarnings({"FutureReturnValueIgnored", "UnusedVariable"})
  private void add(
      final @NotNull MetricType type,
      final @NotNull String key,
      final double value,
      @Nullable MeasurementUnit unit,
      final @Nullable Map<String, String> tags,
      final long timestampMs,
      final int stackLevel,
      @Nullable LocalMetricsAggregator localMetricsAggregator) {

    if (isClosed) {
      return;
    }

    if (beforeEmitCallback != null) {
      if (!beforeEmitCallback.execute(key, tags)) {
        return;
      }
    }

    final long timeBucketKey = MetricsHelper.getTimeBucketKey(timestampMs);
    final @NotNull Map<String, Metric> timeBucket = getOrAddTimeBucket(timeBucketKey);

    final @NotNull String metricKey = MetricsHelper.getMetricBucketKey(type, key, unit, tags);

    final int addedWeight;

    // TODO ideally we can synchronize only the metric itself
    synchronized (timeBucket) {
      @Nullable Metric existingMetric = timeBucket.get(metricKey);
      if (existingMetric != null) {
        final int oldWeight = existingMetric.getWeight();
        existingMetric.add(value);
        addedWeight = existingMetric.getWeight() - oldWeight;
      } else {
        final @NotNull Metric metric;
        switch (type) {
          case Counter:
            metric = new CounterMetric(key, value, unit, tags);
            break;
          case Gauge:
            metric = new GaugeMetric(key, value, unit, tags);
            break;
          case Distribution:
            metric = new DistributionMetric(key, value, unit, tags);
            break;
          case Set:
            metric = new SetMetric(key, unit, tags);
            // sets API is either ints or strings cr32 encoded into ints
            // noinspection unchecked
            metric.add((int) value);
            break;
          default:
            throw new IllegalArgumentException("Unknown MetricType: " + type.name());
        }
        addedWeight = metric.getWeight();
        timeBucket.put(metricKey, metric);
      }
      totalBucketsWeight.addAndGet(addedWeight);
    }
    if (localMetricsAggregator != null) {
      final double localValue = type == MetricType.Set ? addedWeight : value;
      localMetricsAggregator.add(metricKey, type, key, localValue, unit, tags, timestampMs);
    }

    final boolean isOverWeight = isOverWeight();
    if (!isClosed && (isOverWeight || !flushScheduled)) {
      synchronized (this) {
        if (!isClosed) {
          // TODO this is probably not a good idea after all
          // as it will slow down the first metric emission
          // maybe move to constructor?
          if (executorService instanceof NoOpSentryExecutorService) {
            executorService = new SentryExecutorService();
          }

          flushScheduled = true;
          final long delayMs = isOverWeight ? 0 : MetricsHelper.FLUSHER_SLEEP_TIME_MS;
          executorService.schedule(this, delayMs);
        }
      }
    }
  }

  @Override
  public void flush(boolean force) {
    if (!force && isOverWeight()) {
      logger.log(SentryLevel.INFO, "Metrics: total weight exceeded, flushing all buckets");
      force = true;
    }

    final @NotNull Set<Long> flushableBuckets = getFlushableBuckets(force);
    if (flushableBuckets.isEmpty()) {
      logger.log(SentryLevel.DEBUG, "Metrics: nothing to flush");
      return;
    }
    logger.log(SentryLevel.DEBUG, "Metrics: flushing " + flushableBuckets.size() + " buckets");

    final Map<Long, Map<String, Metric>> snapshot = new HashMap<>();
    int numMetrics = 0;
    for (long bucketKey : flushableBuckets) {
      final @Nullable Map<String, Metric> bucket = buckets.remove(bucketKey);
      if (bucket != null) {
        synchronized (bucket) {
          final int weight = getBucketWeight(bucket);
          totalBucketsWeight.addAndGet(-weight);

          numMetrics += bucket.size();
          snapshot.put(bucketKey, bucket);
        }
      }
    }

    if (numMetrics == 0) {
      logger.log(SentryLevel.DEBUG, "Metrics: only empty buckets found");
      return;
    }

    logger.log(SentryLevel.DEBUG, "Metrics: capturing metrics");
    client.captureMetrics(new EncodedMetrics(snapshot));
  }

  private boolean isOverWeight() {
    final int totalWeight = buckets.size() + totalBucketsWeight.get();
    return totalWeight >= maxWeight;
  }

  private static int getBucketWeight(final @NotNull Map<String, Metric> bucket) {
    int weight = 0;
    for (final @NotNull Metric value : bucket.values()) {
      weight += value.getWeight();
    }
    return weight;
  }

  @NotNull
  private Set<Long> getFlushableBuckets(final boolean force) {
    if (force) {
      return buckets.keySet();
    } else {
      // get all keys, including the cutoff key
      final long cutoffTimestampMs = MetricsHelper.getCutoffTimestampMs(nowMillis());
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
      // the same bucket at the same time, overwriting each other
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

  private long nowMillis() {
    return TimeUnit.NANOSECONDS.toMillis(dateProvider.now().nanoTimestamp());
  }
}
