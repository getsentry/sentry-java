package io.sentry.metrics;

import java.nio.charset.Charset;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

/**
 * EncodedMetrics is a class that represents a collection of aggregated metrics, grouped by buckets.
 */
@ApiStatus.Internal
public final class EncodedMetrics {
  @SuppressWarnings({"CharsetObjectCanBeUsed"})
  private static final Charset UTF8 = Charset.forName("UTF-8");

  private final Map<Long, Map<String, Metric>> buckets;

  public EncodedMetrics(final @NotNull Map<Long, Map<String, Metric>> buckets) {
    this.buckets = buckets;
  }

  /**
   * Encodes the metrics into a Statsd compatible format.
   *
   * <p>See <a href="https://github.com/statsd/statsd#usage">github.com/statsd/statsd#usage</a> and
   * <a
   * href="https://getsentry.github.io/relay/relay_metrics/index.html">getsentry.github.io/relay/relay_metrics/index.html</a>
   * for more details about the format.
   *
   * @return the encoded metrics
   */
  public byte[] encodeToStatsd() {
    final StringBuilder statsd = new StringBuilder();
    for (Map.Entry<Long, Map<String, Metric>> entry : buckets.entrySet()) {
      MetricsHelper.encodeMetrics(entry.getKey(), entry.getValue().values(), statsd);
    }
    return statsd.toString().getBytes(UTF8);
  }

  @TestOnly
  Map<Long, Map<String, Metric>> getBuckets() {
    return buckets;
  }
}
