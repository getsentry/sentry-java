package io.sentry.protocol;

import io.sentry.DateUtils;
import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.ObjectReader;
import io.sentry.ObjectWriter;
import io.sentry.SentryLevel;
import io.sentry.Span;
import io.sentry.SpanId;
import io.sentry.SpanStatus;
import io.sentry.featureflags.IFeatureFlagBuffer;
import io.sentry.util.CollectionUtils;
import io.sentry.util.Objects;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SentrySpan implements JsonUnknown, JsonSerializable {
  private final @NotNull Double startTimestamp;
  private final @Nullable Double timestamp;
  private final @NotNull SentryId traceId;
  private final @NotNull SpanId spanId;
  private final @Nullable SpanId parentSpanId;
  private final @NotNull String op;
  private final @Nullable String description;
  private final @Nullable SpanStatus status;

  private final @Nullable String origin;
  private final @NotNull Map<String, String> tags;
  private @Nullable Map<String, Object> data;

  private final @NotNull Map<String, @NotNull MeasurementValue> measurements;

  @SuppressWarnings("unused")
  private @Nullable Map<String, Object> unknown;

  public SentrySpan(final @NotNull Span span) {
    this(span, span.getData());
  }

  @ApiStatus.Internal
  public SentrySpan(final @NotNull Span span, final @Nullable Map<String, Object> data) {
    Objects.requireNonNull(span, "span is required");
    this.description = span.getDescription();
    this.op = span.getOperation();
    this.spanId = span.getSpanId();
    this.parentSpanId = span.getParentSpanId();
    this.traceId = span.getTraceId();
    this.status = span.getStatus();
    this.origin = span.getSpanContext().getOrigin();
    final Map<String, String> tagsCopy = CollectionUtils.newConcurrentHashMap(span.getTags());
    this.tags = tagsCopy != null ? tagsCopy : new ConcurrentHashMap<>();
    final Map<String, MeasurementValue> measurementsCopy =
        CollectionUtils.newConcurrentHashMap(span.getMeasurements());
    this.measurements = measurementsCopy != null ? measurementsCopy : new ConcurrentHashMap<>();
    // we lose precision here, from potential nanosecond precision down to 10 microsecond precision
    this.timestamp =
        span.getFinishDate() == null
            ? null
            : DateUtils.nanosToSeconds(
                span.getStartDate().laterDateNanosTimestampByDiff(span.getFinishDate()));
    // we lose precision here, from potential nanosecond precision down to 10 microsecond precision
    this.startTimestamp = DateUtils.nanosToSeconds(span.getStartDate().nanoTimestamp());
    this.data = data;
    final @NotNull IFeatureFlagBuffer featureFlagBuffer = span.getFeatureFlagBuffer();
    final @Nullable FeatureFlags featureFlags = featureFlagBuffer.getFeatureFlags();
    if (featureFlags != null && data != null) {
      for (FeatureFlag featureFlag : featureFlags.getValues()) {
        data.put("flag.evaluation." + featureFlag.getFlag(), featureFlag.getResult());
      }
    }
  }

  @ApiStatus.Internal
  public SentrySpan(
      @NotNull Double startTimestamp,
      @Nullable Double timestamp,
      @NotNull SentryId traceId,
      @NotNull SpanId spanId,
      @Nullable SpanId parentSpanId,
      @NotNull String op,
      @Nullable String description,
      @Nullable SpanStatus status,
      @Nullable String origin,
      @NotNull Map<String, String> tags,
      @NotNull Map<String, MeasurementValue> measurements,
      @Nullable Map<String, Object> data) {
    this.startTimestamp = startTimestamp;
    this.timestamp = timestamp;
    this.traceId = traceId;
    this.spanId = spanId;
    this.parentSpanId = parentSpanId;
    this.op = op;
    this.description = description;
    this.status = status;
    this.origin = origin;
    this.tags = tags;
    this.measurements = measurements;
    this.data = data;
  }

  public boolean isFinished() {
    return this.timestamp != null;
  }

  public @NotNull Double getStartTimestamp() {
    return startTimestamp;
  }

  public @Nullable Double getTimestamp() {
    return timestamp;
  }

  public @NotNull SentryId getTraceId() {
    return traceId;
  }

  public @NotNull SpanId getSpanId() {
    return spanId;
  }

  public @Nullable SpanId getParentSpanId() {
    return parentSpanId;
  }

  public @NotNull String getOp() {
    return op;
  }

  public @Nullable String getDescription() {
    return description;
  }

  public @Nullable SpanStatus getStatus() {
    return status;
  }

  public @NotNull Map<String, String> getTags() {
    return tags;
  }

  public @Nullable Map<String, Object> getData() {
    return data;
  }

  public void setData(final @Nullable Map<String, Object> data) {
    this.data = data;
  }

  public @Nullable String getOrigin() {
    return origin;
  }

  public @NotNull Map<String, MeasurementValue> getMeasurements() {
    return measurements;
  }

  // JsonSerializable

  public static final class JsonKeys {
    public static final String START_TIMESTAMP = "start_timestamp";
    public static final String TIMESTAMP = "timestamp";
    public static final String TRACE_ID = "trace_id";
    public static final String SPAN_ID = "span_id";
    public static final String PARENT_SPAN_ID = "parent_span_id";
    public static final String OP = "op";
    public static final String DESCRIPTION = "description";
    public static final String STATUS = "status";
    public static final String ORIGIN = "origin";
    public static final String TAGS = "tags";
    public static final String MEASUREMENTS = "measurements";
    public static final String DATA = "data";
  }

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    writer.name(JsonKeys.START_TIMESTAMP).value(logger, doubleToBigDecimal(startTimestamp));
    if (timestamp != null) {
      writer.name(JsonKeys.TIMESTAMP).value(logger, doubleToBigDecimal(timestamp));
    }
    writer.name(JsonKeys.TRACE_ID).value(logger, traceId);
    writer.name(JsonKeys.SPAN_ID).value(logger, spanId);
    if (parentSpanId != null) {
      writer.name(JsonKeys.PARENT_SPAN_ID).value(logger, parentSpanId);
    }
    writer.name(JsonKeys.OP).value(op);
    if (description != null) {
      writer.name(JsonKeys.DESCRIPTION).value(description);
    }
    if (status != null) {
      writer.name(JsonKeys.STATUS).value(logger, status);
    }
    if (origin != null) {
      writer.name(JsonKeys.ORIGIN).value(logger, origin);
    }
    if (!tags.isEmpty()) {
      writer.name(JsonKeys.TAGS).value(logger, tags);
    }
    if (data != null) {
      writer.name(JsonKeys.DATA).value(logger, data);
    }
    if (!measurements.isEmpty()) {
      writer.name(JsonKeys.MEASUREMENTS).value(logger, measurements);
    }
    if (unknown != null) {
      for (String key : unknown.keySet()) {
        Object value = unknown.get(key);
        writer.name(key);
        writer.value(logger, value);
      }
    }
    writer.endObject();
  }

  private @NotNull BigDecimal doubleToBigDecimal(final @NotNull Double value) {
    return BigDecimal.valueOf(value).setScale(6, RoundingMode.DOWN);
  }

  @Nullable
  @Override
  public Map<String, Object> getUnknown() {
    return unknown;
  }

  @Override
  public void setUnknown(@Nullable Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  public static final class Deserializer implements JsonDeserializer<SentrySpan> {

    @SuppressWarnings("unchecked")
    @Override
    public @NotNull SentrySpan deserialize(@NotNull ObjectReader reader, @NotNull ILogger logger)
        throws Exception {
      reader.beginObject();

      Double startTimestamp = null;
      Double timestamp = null;
      SentryId traceId = null;
      SpanId spanId = null;
      SpanId parentSpanId = null;
      String op = null;
      String description = null;
      SpanStatus status = null;
      String origin = null;
      Map<String, String> tags = null;
      Map<String, MeasurementValue> measurements = null;
      Map<String, Object> data = null;

      Map<String, Object> unknown = null;
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.START_TIMESTAMP:
            try {
              startTimestamp = reader.nextDoubleOrNull();
            } catch (NumberFormatException e) {
              final Date date = reader.nextDateOrNull(logger);
              startTimestamp = date != null ? DateUtils.dateToSeconds(date) : null;
            }
            break;
          case JsonKeys.TIMESTAMP:
            try {
              timestamp = reader.nextDoubleOrNull();
            } catch (NumberFormatException e) {
              final Date date = reader.nextDateOrNull(logger);
              timestamp = date != null ? DateUtils.dateToSeconds(date) : null;
            }
            break;
          case JsonKeys.TRACE_ID:
            traceId = new SentryId.Deserializer().deserialize(reader, logger);
            break;
          case JsonKeys.SPAN_ID:
            spanId = new SpanId.Deserializer().deserialize(reader, logger);
            break;
          case JsonKeys.PARENT_SPAN_ID:
            parentSpanId = reader.nextOrNull(logger, new SpanId.Deserializer());
            break;
          case JsonKeys.OP:
            op = reader.nextStringOrNull();
            break;
          case JsonKeys.DESCRIPTION:
            description = reader.nextStringOrNull();
            break;
          case JsonKeys.STATUS:
            status = reader.nextOrNull(logger, new SpanStatus.Deserializer());
            break;
          case JsonKeys.ORIGIN:
            origin = reader.nextStringOrNull();
            break;
          case JsonKeys.TAGS:
            tags = (Map<String, String>) reader.nextObjectOrNull();
            break;
          case JsonKeys.DATA:
            data = (Map<String, Object>) reader.nextObjectOrNull();
            break;
          case JsonKeys.MEASUREMENTS:
            measurements = reader.nextMapOrNull(logger, new MeasurementValue.Deserializer());
            break;
          default:
            if (unknown == null) {
              unknown = new ConcurrentHashMap<>();
            }
            reader.nextUnknown(logger, unknown, nextName);
            break;
        }
      }
      if (startTimestamp == null) {
        throw missingRequiredFieldException(JsonKeys.START_TIMESTAMP, logger);
      }
      if (traceId == null) {
        throw missingRequiredFieldException(JsonKeys.TRACE_ID, logger);
      }
      if (spanId == null) {
        throw missingRequiredFieldException(JsonKeys.SPAN_ID, logger);
      }
      if (op == null) {
        throw missingRequiredFieldException(JsonKeys.OP, logger);
      }
      if (tags == null) {
        tags = new HashMap<>();
      }
      if (measurements == null) {
        measurements = new HashMap<>();
      }
      SentrySpan sentrySpan =
          new SentrySpan(
              startTimestamp,
              timestamp,
              traceId,
              spanId,
              parentSpanId,
              op,
              description,
              status,
              origin,
              tags,
              measurements,
              data);
      sentrySpan.setUnknown(unknown);
      reader.endObject();
      return sentrySpan;
    }

    private Exception missingRequiredFieldException(String field, ILogger logger) {
      String message = "Missing required field \"" + field + "\"";
      Exception exception = new IllegalStateException(message);
      logger.log(SentryLevel.ERROR, message, exception);
      return exception;
    }
  }
}
