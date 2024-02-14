package io.sentry.metrics;

import io.sentry.MeasurementUnit;
import java.util.Calendar;
import java.util.Collection;
import java.util.Map;
import java.util.Random;
import java.util.TimeZone;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class MetricHelper {
  public static final int FLUSHER_SLEEP_TIME_MS = 5000;
  private static final int ROLLUP_IN_SECONDS = 10;

  private static final String INVALID_KEY_CHARACTERS_PATTERN = "[^a-zA-Z0-9_/.-]+";
  private static final String INVALID_VALUE_CHARACTERS_PATTERN = "[^\\w\\d_:/@\\.\\{\\}\\[\\]$-]+";

  private static final char TAGS_PAIR_DELIMITER = ','; // Delimiter between key-value pairs
  private static final char TAGS_KEY_VALUE_DELIMITER = '='; // Delimiter between key and value
  private static final char TAGS_ESCAPE_CHAR = '\\';

  private static final double FLUSH_SHIFT_MS =
      (long) (new Random().nextFloat() * (ROLLUP_IN_SECONDS * 1000f));

  public static long getDayBucketKey(final @NotNull Calendar timestamp) {
    final Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    utc.set(Calendar.YEAR, timestamp.get(Calendar.YEAR));
    utc.set(Calendar.MONTH, timestamp.get(Calendar.MONTH));
    utc.set(Calendar.DAY_OF_MONTH, timestamp.get(Calendar.DAY_OF_MONTH));

    return utc.getTimeInMillis() / 1000;
  }

  public static long getTimeBucketKey(final @NotNull Calendar timestamp) {
    final long seconds = timestamp.getTimeInMillis() / 1000;
    return (seconds / ROLLUP_IN_SECONDS) * ROLLUP_IN_SECONDS;
  }

  public static double getFlushShiftMs() {
    return FLUSH_SHIFT_MS;
  }

  public static Calendar getCutoff() {
    final Calendar cutOff = Calendar.getInstance();
    cutOff.add(Calendar.SECOND, -ROLLUP_IN_SECONDS);
    cutOff.add(Calendar.MILLISECOND, (int) -FLUSH_SHIFT_MS);
    return cutOff;
  }

  public static @NotNull String sanitizeKey(final @NotNull String input) {
    return input.replaceAll(INVALID_KEY_CHARACTERS_PATTERN, "_");
  }

  public static String sanitizeValue(final @NotNull String input) {
    return input.replaceAll(INVALID_VALUE_CHARACTERS_PATTERN, "_");
  }

  public static @NotNull String toStatsdType(final @NotNull MetricType type) {
    switch (type) {
      case Counter:
        return "c";
      case Gauge:
        return "g";
      case Distribution:
        return "d";
      case Set:
        return "s";
      default:
        throw new IllegalArgumentException("Invalid Metric Type: " + type.name());
    }
  }

  @NotNull
  public static String getMetricBucketKey(
      final @NotNull MetricType type,
      final @NotNull String metricKey,
      final @Nullable MeasurementUnit unit,
      final @Nullable Map<String, String> tags) {
    final @NotNull String typePrefix = toStatsdType(type);
    final @NotNull String serializedTags = GetTagsKey(tags);

    return String.format("%s_%s_%s_%s", typePrefix, metricKey, unit, serializedTags);
  }

  private static String GetTagsKey(final @Nullable Map<String, String> tags) {
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
   * @param timestamp The bucket time the metrics belong to, in second resolution
   * @param metrics The metrics to encode
   * @param writer The writer to encode the metrics into
   */
  public static void encodeMetrics(
      final long timestamp,
      final @NotNull Collection<Metric> metrics,
      final @NotNull StringBuilder writer) {
    for (Metric metric : metrics) {
      writer.append(sanitizeKey(metric.getKey()));
      writer.append("@");

      final MeasurementUnit unit = metric.getUnit();
      final String unitName = (unit != null) ? unit.apiName() : MeasurementUnit.NONE;
      writer.append(unitName);

      for (final @NotNull Object value : metric.getValues()) {
        writer.append(":");
        writer.append(value);
      }

      writer.append("|");
      writer.append(toStatsdType(metric.getType()));

      final @Nullable Map<String, String> tags = metric.getTags();
      if (tags != null) {
        writer.append("|#");
        boolean first = true;
        for (final @NotNull Map.Entry<String, String> tag : tags.entrySet()) {
          final @NotNull String tagKey = sanitizeKey(tag.getKey());
          if (first) {
            first = false;
          } else {
            writer.append(",");
          }
          writer.append(tagKey);
          writer.append(":");
          writer.append(sanitizeValue(tag.getValue()));
        }
      }

      writer.append("|T");
      writer.append(timestamp);
      writer.append("\n");
    }
  }
}
