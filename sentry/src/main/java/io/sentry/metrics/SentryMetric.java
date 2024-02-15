package io.sentry.metrics;

import io.sentry.ILogger;
import io.sentry.JsonSerializable;
import io.sentry.MeasurementUnit;
import io.sentry.ObjectWriter;
import io.sentry.protocol.SentryId;
import java.io.IOException;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// TODO actually needed? we seem to send them in statsd format anyway
@ApiStatus.Internal
public final class SentryMetric implements JsonSerializable {

  private final Iterable<?> values;

  public static final class JsonKeys {
    public static final String TYPE = "type";
    public static final String EVENT_ID = "event_id";
    public static final String NAME = "name";
    public static final String TIMESTAMP = "timestamp";
    public static final String UNIT = "unit";
    public static final String TAGS = "tags";
    public static final String VALUE = "value";
  }

  private final @NotNull MetricType type;
  private final @NotNull SentryId eventId;
  private final @NotNull String key;
  private final @Nullable MeasurementUnit unit;
  private final @Nullable Map<String, String> tags;
  private final long timestampMs;

  public SentryMetric(@NotNull Metric metric) {
    this.eventId = new SentryId();

    this.type = metric.getType();
    this.key = metric.getKey();
    this.unit = metric.getUnit();
    this.tags = metric.getTags();
    this.timestampMs = metric.getTimeStampMs();
    this.values = metric.getValues();
  }

  @Override
  public void serialize(@NotNull ObjectWriter writer, @NotNull ILogger logger) throws IOException {

    writer.beginObject();
    writer.name(JsonKeys.TYPE).value(MetricsHelper.toStatsdType(type));
    writer.name(JsonKeys.EVENT_ID).value(logger, eventId);
    writer.name(JsonKeys.NAME).value(key);
    writer.name(JsonKeys.TIMESTAMP).value((double) timestampMs / 1000.0d);
    if (unit != null) {
      writer.name(JsonKeys.UNIT).value(unit.apiName());
    }
    if (tags != null) {
      writer.name(JsonKeys.TAGS);
      writer.beginObject();
      for (final @NotNull Map.Entry<String, String> entry : tags.entrySet()) {
        writer.name(entry.getKey()).value(entry.getValue());
      }
      writer.endObject();
    }

    writer.name(JsonKeys.VALUE);
    writer.beginArray();
    for (final Object value : values) {
      writer.value(logger, value);
    }
    writer.endArray();

    writer.endObject();
  }
}
