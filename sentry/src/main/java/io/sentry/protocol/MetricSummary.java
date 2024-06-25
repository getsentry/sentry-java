package io.sentry.protocol;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.ObjectReader;
import io.sentry.ObjectWriter;
import io.sentry.util.CollectionUtils;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A summary for a metric, usually attached to spans. */
public final class MetricSummary implements JsonUnknown, JsonSerializable {

  public static final class JsonKeys {
    public static final String TAGS = "tags";
    public static final String MIN = "min";
    public static final String MAX = "max";
    public static final String COUNT = "count";
    public static final String SUM = "sum";
  }

  private double min;
  private double max;
  private double sum;
  private int count;
  private @Nullable Map<String, String> tags;
  private @Nullable Map<String, Object> unknown;

  public MetricSummary() {}

  public MetricSummary(
      final double min,
      final double max,
      final double sum,
      final int count,
      final @Nullable Map<String, String> tags) {

    this.tags = tags;
    this.min = min;
    this.max = max;
    this.count = count;
    this.sum = sum;

    this.unknown = null;
  }

  public void setTags(final @Nullable Map<String, String> tags) {
    this.tags = tags;
  }

  public void setMin(final double min) {
    this.min = min;
  }

  public void setMax(final double max) {
    this.max = max;
  }

  public void setCount(final int count) {
    this.count = count;
  }

  public void setSum(final double sum) {
    this.sum = sum;
  }

  public double getMin() {
    return min;
  }

  public double getMax() {
    return max;
  }

  public double getSum() {
    return sum;
  }

  public int getCount() {
    return count;
  }

  @Nullable
  public Map<String, String> getTags() {
    return tags;
  }

  @Override
  public @Nullable Map<String, Object> getUnknown() {
    return unknown;
  }

  @Override
  public void setUnknown(final @Nullable Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    writer.name(JsonKeys.MIN).value(min);
    writer.name(JsonKeys.MAX).value(max);
    writer.name(JsonKeys.SUM).value(sum);
    writer.name(JsonKeys.COUNT).value(count);
    if (tags != null) {
      writer.name(JsonKeys.TAGS);
      writer.value(logger, tags);
    }
    writer.endObject();
  }

  @SuppressWarnings("unchecked")
  public static final class Deserializer implements JsonDeserializer<MetricSummary> {

    @Override
    public @NotNull MetricSummary deserialize(
        final @NotNull ObjectReader reader, final @NotNull ILogger logger) throws Exception {

      final @NotNull MetricSummary summary = new MetricSummary();
      @Nullable Map<String, Object> unknown = null;

      reader.beginObject();
      while (reader.peek() == JsonToken.NAME) {
        final @NotNull String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.TAGS:
            summary.tags =
                CollectionUtils.newConcurrentHashMap(
                    (Map<String, String>) reader.nextObjectOrNull());
            break;
          case JsonKeys.MIN:
            summary.setMin(reader.nextDouble());
            break;
          case JsonKeys.MAX:
            summary.setMax(reader.nextDouble());
            break;
          case JsonKeys.SUM:
            summary.setSum(reader.nextDouble());
            break;
          case JsonKeys.COUNT:
            summary.setCount(reader.nextInt());
            break;
          default:
            if (unknown == null) {
              unknown = new ConcurrentHashMap<>();
            }
            reader.nextUnknown(logger, unknown, nextName);
            break;
        }
      }
      summary.setUnknown(unknown);
      reader.endObject();
      return summary;
    }
  }
}
