package io.sentry;

import static io.sentry.DateUtils.doubleToBigDecimal;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import io.sentry.protocol.SentryId;
import io.sentry.vendor.gson.stream.JsonToken;

public final class SentryMetricsEvent implements JsonUnknown, JsonSerializable {

  private @NotNull SentryId traceId;
  private @Nullable SpanId spanId;
  /**
   * Timestamp in seconds (epoch time) indicating when the metric was recorded.
   */
  private @NotNull Double timestamp;
  /**
   * The name of the metric.
   * This should follow a hierarchical naming convention using dots as separators
   * (e.g., api.response_time, db.query.duration).
   */
  private @NotNull String name;
  /**
   * The unit of measurement for the metric value.
   */
  private @Nullable String unit;
  /**
   * The type of metric. One of:
   * - counter: A metric that increments counts
   * - gauge: A metric that tracks a value that can go up or down
   * - distribution: A metric that tracks the statistical distribution of values
   */
  private @NotNull String type;
  /**
   * The numeric value of the metric. The interpretation depends on the metric type:
   * - For counter metrics: the count to increment by (should default to 1)
   * - For gauge metrics: the current value
   * - For distribution metrics: a single measured value
   */
  private @NotNull Double value;

  private @Nullable Map<String, SentryLogEventAttributeValue> attributes;
  private @Nullable Map<String, Object> unknown;

  public SentryMetricsEvent(
      final @NotNull SentryId traceId,
      final @NotNull SentryDate timestamp,
      final @NotNull String name,
      final @NotNull String type,
      final @NotNull Double value) {
    this(traceId, DateUtils.nanosToSeconds(timestamp.nanoTimestamp()), name, type, value);
  }

  public SentryMetricsEvent(
      final @NotNull SentryId traceId,
      final @NotNull Double timestamp,
      final @NotNull String name,
      final @NotNull String type,
      final @NotNull Double value) {
    this.traceId = traceId;
    this.timestamp = timestamp;
    this.name = name;
    this.type = type;
    this.value = value;
  }

  @NotNull
  public Double getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(final @NotNull Double timestamp) {
    this.timestamp = timestamp;
  }

  public @NotNull String getName() {
    return name;
  }

  public void setName(@NotNull String name) {
    this.name = name;
  }

  public @NotNull String getType() {
    return type;
  }

  public void setType(@NotNull String type) {
    this.type = type;
  }

  public @Nullable String getUnit() {
    return unit;
  }

  public void setUnit(@Nullable String unit) {
    this.unit = unit;
  }

  public @Nullable SpanId getSpanId() {
    return spanId;
  }

  public void setSpanId(@Nullable SpanId spanId) {
    this.spanId = spanId;
  }

  public @NotNull Double getValue() {
    return value;
  }

  public void setValue(@NotNull Double value) {
    this.value = value;
  }

  public @Nullable Map<String, SentryLogEventAttributeValue> getAttributes() {
    return attributes;
  }

  public void setAttributes(final @Nullable Map<String, SentryLogEventAttributeValue> attributes) {
    this.attributes = attributes;
  }

  public void setAttribute(
      final @Nullable String key, final @Nullable SentryLogEventAttributeValue value) {
    if (key == null) {
      return;
    }
    if (this.attributes == null) {
      this.attributes = new HashMap<>();
    }
    this.attributes.put(key, value);
  }

  // region json
  public static final class JsonKeys {
    public static final String TIMESTAMP = "timestamp";
    public static final String TRACE_ID = "trace_id";
    public static final String SPAN_ID = "span_id";
    public static final String NAME = "name";
    public static final String TYPE = "type";
    public static final String UNIT = "unit";
    public static final String VALUE = "value";
    public static final String ATTRIBUTES = "attributes";
  }

  @Override
  @SuppressWarnings("JdkObsolete")
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    writer.name(JsonKeys.TIMESTAMP).value(logger, doubleToBigDecimal(timestamp));
    writer.name(JsonKeys.TYPE).value(type);
    writer.name(JsonKeys.NAME).value(name);
    writer.name(JsonKeys.VALUE).value(value);
    writer.name(JsonKeys.TRACE_ID).value(logger, traceId);
    if (spanId != null) {
      writer.name(JsonKeys.SPAN_ID).value(logger, spanId);
    }
    if (unit != null) {
      writer.name(JsonKeys.UNIT).value(logger, unit);
    }
    if (attributes != null) {
      writer.name(JsonKeys.ATTRIBUTES).value(logger, attributes);
    }

    if (unknown != null) {
      for (String key : unknown.keySet()) {
        Object value = unknown.get(key);
        writer.name(key).value(logger, value);
      }
    }
    writer.endObject();
  }

  @Override
  public @Nullable Map<String, Object> getUnknown() {
    return unknown;
  }

  @Override
  public void setUnknown(final @Nullable Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  public static final class Deserializer implements JsonDeserializer<SentryMetricsEvent> {

    @SuppressWarnings("unchecked")
    @Override
    public @NotNull SentryMetricsEvent deserialize(
        final @NotNull ObjectReader reader, final @NotNull ILogger logger) throws Exception {
      @Nullable Map<String, Object> unknown = null;
      @Nullable SentryId traceId = null;
      @Nullable SpanId spanId = null;
      @Nullable Double timestamp = null;
      @Nullable String type = null;
      @Nullable String name = null;
      @Nullable String unit = null;
      @Nullable Double value = null;
      @Nullable Map<String, SentryLogEventAttributeValue> attributes = null;

      reader.beginObject();
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.TRACE_ID:
            traceId = reader.nextOrNull(logger, new SentryId.Deserializer());
            break;
          case JsonKeys.SPAN_ID:
            spanId = reader.nextOrNull(logger, new SpanId.Deserializer());
            break;
          case JsonKeys.TIMESTAMP:
            timestamp = reader.nextDoubleOrNull();
            break;
          case JsonKeys.TYPE:
            type = reader.nextStringOrNull();
            break;
          case JsonKeys.NAME:
            name = reader.nextStringOrNull();
            break;
          case JsonKeys.UNIT:
            unit = reader.nextStringOrNull();
            break;
          case JsonKeys.VALUE:
            value = reader.nextDoubleOrNull();
            break;
          case JsonKeys.ATTRIBUTES:
            attributes =
                reader.nextMapOrNull(logger, new SentryLogEventAttributeValue.Deserializer());
            break;
          default:
            if (unknown == null) {
              unknown = new HashMap<>();
            }
            reader.nextUnknown(logger, unknown, nextName);
            break;
        }
      }
      reader.endObject();

      if (traceId == null) {
        String message = "Missing required field \"" + JsonKeys.TRACE_ID + "\"";
        Exception exception = new IllegalStateException(message);
        logger.log(SentryLevel.ERROR, message, exception);
        throw exception;
      }

      if (timestamp == null) {
        String message = "Missing required field \"" + JsonKeys.TIMESTAMP + "\"";
        Exception exception = new IllegalStateException(message);
        logger.log(SentryLevel.ERROR, message, exception);
        throw exception;
      }

      if (type == null) {
        String message = "Missing required field \"" + JsonKeys.TYPE + "\"";
        Exception exception = new IllegalStateException(message);
        logger.log(SentryLevel.ERROR, message, exception);
        throw exception;
      }

      if (name == null) {
        String message = "Missing required field \"" + JsonKeys.NAME + "\"";
        Exception exception = new IllegalStateException(message);
        logger.log(SentryLevel.ERROR, message, exception);
        throw exception;
      }

      if (value == null) {
        String message = "Missing required field \"" + JsonKeys.VALUE + "\"";
        Exception exception = new IllegalStateException(message);
        logger.log(SentryLevel.ERROR, message, exception);
        throw exception;
      }

      final SentryMetricsEvent logEvent = new SentryMetricsEvent(traceId, timestamp, name, type, value);

      logEvent.setAttributes(attributes);
      logEvent.setSpanId(spanId);
      logEvent.setUnit(unit);
      logEvent.setUnknown(unknown);

      return logEvent;
    }
  }
  // endregion json
}
