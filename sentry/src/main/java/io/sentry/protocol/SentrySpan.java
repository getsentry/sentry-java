package io.sentry.protocol;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonObjectReader;
import io.sentry.JsonObjectWriter;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.SentryLevel;
import io.sentry.Span;
import io.sentry.SpanId;
import io.sentry.SpanStatus;
import io.sentry.util.CollectionUtils;
import io.sentry.util.Objects;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SentrySpan implements JsonUnknown, JsonSerializable {
  private final @NotNull Date startTimestamp;
  private final @Nullable Date timestamp;
  private final @NotNull SentryId traceId;
  private final @NotNull SpanId spanId;
  private final @Nullable SpanId parentSpanId;
  private final @NotNull String op;
  private final @Nullable String description;
  private final @Nullable SpanStatus status;
  private final @NotNull Map<String, String> tags;
  private final @Nullable Map<String, Object> data;

  @SuppressWarnings("unused")
  private @Nullable Map<String, Object> unknown;

  public SentrySpan(final @NotNull Span span) {
    this(span, null);
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
    final Map<String, String> tagsCopy = CollectionUtils.newConcurrentHashMap(span.getTags());
    this.tags = tagsCopy != null ? tagsCopy : new ConcurrentHashMap<>();
    this.timestamp = span.getTimestamp();
    this.startTimestamp = span.getStartTimestamp();
    this.data = data;
  }

  @ApiStatus.Internal
  public SentrySpan(
      @NotNull Date startTimestamp,
      @Nullable Date timestamp,
      @NotNull SentryId traceId,
      @NotNull SpanId spanId,
      @Nullable SpanId parentSpanId,
      @NotNull String op,
      @Nullable String description,
      @Nullable SpanStatus status,
      @NotNull Map<String, String> tags,
      @Nullable Map<String, Object> data) {
    this.startTimestamp = startTimestamp;
    this.timestamp = timestamp;
    this.traceId = traceId;
    this.spanId = spanId;
    this.parentSpanId = parentSpanId;
    this.op = op;
    this.description = description;
    this.status = status;
    this.tags = tags;
    this.data = data;
  }

  public boolean isFinished() {
    return this.timestamp != null;
  }

  public @NotNull Date getStartTimestamp() {
    return startTimestamp;
  }

  public @Nullable Date getTimestamp() {
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
    public static final String TAGS = "tags";
    public static final String DATA = "data";
  }

  @Override
  public void serialize(@NotNull JsonObjectWriter writer, @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    writer.name(JsonKeys.START_TIMESTAMP).value(logger, startTimestamp);
    if (timestamp != null) {
      writer.name(JsonKeys.TIMESTAMP).value(logger, timestamp);
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
    if (!tags.isEmpty()) {
      writer.name(JsonKeys.TAGS).value(logger, tags);
    }
    if (data != null) {
      writer.name(JsonKeys.DATA).value(logger, data);
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
    public @NotNull SentrySpan deserialize(
        @NotNull JsonObjectReader reader, @NotNull ILogger logger) throws Exception {
      reader.beginObject();

      Date startTimestamp = null; // @NotNull
      Date timestamp = null;
      SentryId traceId = null; // @NotNull
      SpanId spanId = null; // @NotNull
      SpanId parentSpanId = null;
      String op = null; // @NotNull
      String description = null;
      SpanStatus status = null;
      Map<String, String> tags = null; // @NotNull
      Map<String, Object> data = null;

      Map<String, Object> unknown = null;
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.START_TIMESTAMP:
            startTimestamp = reader.nextDateOrNull(logger);
            break;
          case JsonKeys.TIMESTAMP:
            timestamp = reader.nextDateOrNull(logger);
            break;
          case JsonKeys.TRACE_ID:
            traceId = new SentryId.Deserializer().deserialize(reader, logger);
            break;
          case JsonKeys.SPAN_ID:
            spanId = new SpanId.Deserializer().deserialize(reader, logger);
            break;
          case JsonKeys.PARENT_SPAN_ID:
            parentSpanId = new SpanId.Deserializer().deserialize(reader, logger);
            break;
          case JsonKeys.OP:
            op = reader.nextStringOrNull();
            break;
          case JsonKeys.DESCRIPTION:
            description = reader.nextStringOrNull();
            break;
          case JsonKeys.STATUS:
            status = new SpanStatus.Deserializer().deserialize(reader, logger);
            break;
          case JsonKeys.TAGS:
            tags = (Map<String, String>) reader.nextObjectOrNull();
            break;
          case JsonKeys.DATA:
            data = (Map<String, Object>) reader.nextObjectOrNull();
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
        throw missingRequiredFieldException(JsonKeys.TAGS, logger);
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
              tags,
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
