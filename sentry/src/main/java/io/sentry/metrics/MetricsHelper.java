package io.sentry.metrics;

import io.sentry.MeasurementUnit;
import io.sentry.util.Random;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

@ApiStatus.Internal
public final class MetricsHelper {
  public static final long FLUSHER_SLEEP_TIME_MS = 5000;
  public static final int MAX_TOTAL_WEIGHT = 100000;
  private static final int ROLLUP_IN_SECONDS = 10;

  private static final Pattern UNIT_PATTERN = Pattern.compile("\\W+");
  private static final Pattern NAME_PATTERN = Pattern.compile("[^\\w\\-.]+");
  private static final Pattern TAG_KEY_PATTERN = Pattern.compile("[^\\w\\-./]+");

  private static final char TAGS_PAIR_DELIMITER = ','; // Delimiter between key-value pairs
  private static final char TAGS_KEY_VALUE_DELIMITER = '='; // Delimiter between key and value
  private static final char TAGS_ESCAPE_CHAR = '\\';

  private static long FLUSH_SHIFT_MS =
      (long) (new Random().nextFloat() * (ROLLUP_IN_SECONDS * 1000f));

  public static long getTimeBucketKey(final long timestampMs) {
    final long seconds = timestampMs / 1000;
    final long bucketKey = (seconds / ROLLUP_IN_SECONDS) * ROLLUP_IN_SECONDS;
    // this will result into timestamps of -9999...9999 to fall into a ~20s bucket
    // simply shift the bucket key for negative timestamp values to ensure those two are apart
    if (timestampMs >= 0) {
      return bucketKey;
    } else return bucketKey - 1;
  }

  public static long getCutoffTimestampMs(final long nowMs) {
    return nowMs - (ROLLUP_IN_SECONDS * 1000) - FLUSH_SHIFT_MS;
  }

  @NotNull
  public static String sanitizeUnit(final @NotNull String unit) {
    return UNIT_PATTERN.matcher(unit).replaceAll("");
  }

  @NotNull
  public static String sanitizeName(final @NotNull String input) {
    return NAME_PATTERN.matcher(input).replaceAll("_");
  }

  @NotNull
  public static String sanitizeTagKey(final @NotNull String input) {
    return TAG_KEY_PATTERN.matcher(input).replaceAll("");
  }

  @NotNull
  public static String sanitizeTagValue(final @NotNull String input) {
    // see https://develop.sentry.dev/sdk/metrics/#tag-values-replacement-map
    // Line feed       -> \n
    // Carriage return -> \r
    // Tab             -> \t
    // Backslash       -> \\
    // Pipe            -> \\u{7c}
    // Comma           -> \\u{2c}
    final StringBuilder output = new StringBuilder(input.length());
    for (int idx = 0; idx < input.length(); idx++) {
      final char ch = input.charAt(idx);
      if (ch == '\n') {
        output.append("\\n");
      } else if (ch == '\r') {
        output.append("\\r");
      } else if (ch == '\t') {
        output.append("\\t");
      } else if (ch == '\\') {
        output.append("\\\\");
      } else if (ch == '|') {
        output.append("\\u{7c}");
      } else if (ch == ',') {
        output.append("\\u{2c}");
      } else {
        output.append(ch);
      }
    }
    return output.toString();
  }

  @NotNull
  public static String getMetricBucketKey(
      final @NotNull MetricType type,
      final @NotNull String metricKey,
      final @Nullable MeasurementUnit unit,
      final @Nullable Map<String, String> tags) {
    final @NotNull String typePrefix = type.statsdCode;
    final @NotNull String serializedTags = getTagsKey(tags);

    final @NotNull String unitName = getUnitName(unit);
    return String.format("%s_%s_%s_%s", typePrefix, metricKey, unitName, serializedTags);
  }

  @NotNull
  private static String getUnitName(final @Nullable MeasurementUnit unit) {
    return (unit != null) ? unit.apiName() : MeasurementUnit.NONE;
  }

  @NotNull
  private static String getTagsKey(final @Nullable Map<String, String> tags) {
    if (tags == null || tags.isEmpty()) {
      return "";
    }

    final @NotNull StringBuilder builder = new StringBuilder();
    for (Map.Entry<String, String> tag : tags.entrySet()) {

      // Escape delimiters in key and value
      final @NotNull String key = escapeString(tag.getKey());
      final @NotNull String value = escapeString(tag.getValue());

      if (builder.length() > 0) {
        builder.append(TAGS_PAIR_DELIMITER);
      }

      builder.append(key).append(TAGS_KEY_VALUE_DELIMITER).append(value);
    }

    return builder.toString();
  }

  @NotNull
  private static String escapeString(final @NotNull String input) {
    final StringBuilder escapedString = new StringBuilder(input.length());

    for (int idx = 0; idx < input.length(); idx++) {
      final char ch = input.charAt(idx);

      if (ch == TAGS_PAIR_DELIMITER || ch == TAGS_KEY_VALUE_DELIMITER) {
        escapedString.append(TAGS_ESCAPE_CHAR); // Prefix with escape character
      }
      escapedString.append(ch);
    }

    return escapedString.toString();
  }

  /**
   * Provides an export key for identifying the metric without its tags. Suitable for span level
   * metric summaries
   *
   * @param type the metric type
   * @param key the metric key
   * @param unit the metric unit
   * @return the export key
   */
  @NotNull
  public static String getExportKey(
      final @NotNull MetricType type,
      final @NotNull String key,
      final @Nullable MeasurementUnit unit) {
    final @NotNull String unitName = getUnitName(unit);
    return String.format("%s:%s@%s", type.statsdCode, key, unitName);
  }

  public static double convertNanosTo(
      final @NotNull MeasurementUnit.Duration unit, final long durationNanos) {
    switch (unit) {
      case NANOSECOND:
        return durationNanos;
      case MICROSECOND:
        return (double) durationNanos / 1000.0d;
      case MILLISECOND:
        return (double) durationNanos / 1000000.0d;
      case SECOND:
        return (double) durationNanos / 1000000000.0d;
      case MINUTE:
        return (double) durationNanos / 60000000000.0d;
      case HOUR:
        return (double) durationNanos / 3600000000000.0d;
      case DAY:
        return (double) durationNanos / 86400000000000.0d;
      case WEEK:
        return (double) durationNanos / 86400000000000.0d / 7.0d;
      default:
        throw new IllegalArgumentException("Unknown Duration unit: " + unit.name());
    }
  }

  /**
   * Encodes the metrics
   *
   * <p>See <a href="https://github.com/statsd/statsd#usage">github.com/statsd/statsd#usage</a> and
   * <a
   * href="https://getsentry.github.io/relay/relay_metrics/index.html">getsentry.github.io/relay/relay_metrics/index.html</a>
   * for more details about the format.
   *
   * @param timestamp The bucket time the metrics belong to, in second resolution
   * @param metrics The metrics to encode
   * @param writer The writer to encode the metrics into
   */
  public static void encodeMetrics(
      final long timestamp,
      final @NotNull Collection<Metric> metrics,
      final @NotNull StringBuilder writer) {
    for (Metric metric : metrics) {
      writer.append(sanitizeName(metric.getKey()));
      writer.append("@");

      final @Nullable MeasurementUnit unit = metric.getUnit();
      final @NotNull String unitName = getUnitName(unit);
      final String sanitizeUnitName = sanitizeUnit(unitName);
      writer.append(sanitizeUnitName);

      for (final @NotNull Object value : metric.serialize()) {
        writer.append(":");
        writer.append(value);
      }

      writer.append("|");
      writer.append(metric.getType().statsdCode);

      final @Nullable Map<String, String> tags = metric.getTags();
      if (tags != null) {
        writer.append("|#");
        boolean first = true;
        for (final @NotNull Map.Entry<String, String> tag : tags.entrySet()) {
          final @NotNull String tagKey = sanitizeTagKey(tag.getKey());
          if (first) {
            first = false;
          } else {
            writer.append(",");
          }
          writer.append(tagKey);
          writer.append(":");
          writer.append(sanitizeTagValue(tag.getValue()));
        }
      }

      writer.append("|T");
      writer.append(timestamp);
      writer.append("\n");
    }
  }

  @NotNull
  public static Map<String, String> mergeTags(
      final @Nullable Map<String, String> tags, final @NotNull Map<String, String> defaultTags) {
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

  @TestOnly
  public static void setFlushShiftMs(long flushShiftMs) {
    FLUSH_SHIFT_MS = flushShiftMs;
  }
}
