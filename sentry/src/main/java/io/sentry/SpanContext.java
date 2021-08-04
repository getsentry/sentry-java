package io.sentry;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.protocol.SentryId;
import io.sentry.util.CollectionUtils;
import io.sentry.util.Objects;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

@Open
public class SpanContext implements JsonSerializable {
  public static final String TYPE = "trace";

  /** Determines which trace the Span belongs to. */
  private final @NotNull SentryId traceId;

  /** Span id. */
  private final @NotNull SpanId spanId;

  /** Id of a parent span. */
  private final @Nullable SpanId parentSpanId;

  /** If trace is sampled. */
  private transient @Nullable Boolean sampled;

  /** Short code identifying the type of operation the span is measuring. */
  protected @NotNull String op;

  /**
   * Longer description of the span's operation, which uniquely identifies the span but is
   * consistent across instances of the span.
   */
  protected @Nullable String description;

  /** Describes the status of the Transaction. */
  protected @Nullable SpanStatus status;

  /** A map or list of tags for this event. Each tag must be less than 200 characters. */
  protected @NotNull Map<String, @NotNull String> tags = new ConcurrentHashMap<>();

  @SuppressWarnings("unused")
  private @Nullable Map<String, @NotNull Object> unknown;

  public SpanContext(final @NotNull String operation, final @Nullable Boolean sampled) {
    this(new SentryId(), new SpanId(), operation, null, sampled);
  }

  /**
   * Creates trace context with defered sampling decision.
   *
   * @param operation the operation
   */
  public SpanContext(final @NotNull String operation) {
    this(new SentryId(), new SpanId(), operation, null, null);
  }

  public SpanContext(
      final @NotNull SentryId traceId,
      final @NotNull SpanId spanId,
      final @NotNull String operation,
      final @Nullable SpanId parentSpanId,
      final @Nullable Boolean sampled) {
    this.traceId = Objects.requireNonNull(traceId, "traceId is required");
    this.spanId = Objects.requireNonNull(spanId, "spanId is required");
    this.op = Objects.requireNonNull(operation, "operation is required");
    this.parentSpanId = parentSpanId;
    this.sampled = sampled;
  }

  /**
   * Copy constructor.
   *
   * @param spanContext the spanContext to copy
   */
  public SpanContext(final @NotNull SpanContext spanContext) {
    this.traceId = spanContext.traceId;
    this.spanId = spanContext.spanId;
    this.parentSpanId = spanContext.parentSpanId;
    this.sampled = spanContext.sampled;
    this.op = spanContext.op;
    this.description = spanContext.description;
    this.status = spanContext.status;
    final Map<String, String> copiedTags = CollectionUtils.newConcurrentHashMap(spanContext.tags);
    if (copiedTags != null) {
      this.tags = copiedTags;
    }
  }

  public void setOperation(final @NotNull String operation) {
    this.op = Objects.requireNonNull(operation, "operation is required");
  }

  public void setTag(final @NotNull String name, final @NotNull String value) {
    Objects.requireNonNull(name, "name is required");
    Objects.requireNonNull(value, "value is required");
    this.tags.put(name, value);
  }

  public void setDescription(final @Nullable String description) {
    this.description = description;
  }

  public void setStatus(final @Nullable SpanStatus status) {
    this.status = status;
  }

  @NotNull
  public SentryId getTraceId() {
    return traceId;
  }

  @NotNull
  public SpanId getSpanId() {
    return spanId;
  }

  @Nullable
  @TestOnly
  public SpanId getParentSpanId() {
    return parentSpanId;
  }

  public @NotNull String getOperation() {
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

  public @Nullable Boolean getSampled() {
    return sampled;
  }

  void setSampled(final @Nullable Boolean sampled) {
    this.sampled = sampled;
  }

  // region JsonSerializable

  public static final class JsonKeys {
    public static final String TRACE_ID = "trace_id";
    public static final String SPAN_ID = "span_id";
    public static final String PARENT_SPAN_ID = "parent_span_id";
    public static final String SAMPLED = "sampled";
    public static final String OP = "op";
    public static final String DESCRIPTION = "description";
    public static final String STATUS = "status";
    public static final String TAGS = "tags";
  }

  @Override
  public void serialize(@NotNull JsonObjectWriter writer, @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    writer.name(JsonKeys.TRACE_ID);
    new SentryId.Serializer().serialize(traceId, writer);
    writer.name(JsonKeys.SPAN_ID);
    new SpanId.Serializer().serialize(spanId, writer);
    if (parentSpanId != null) {
      writer.name(JsonKeys.PARENT_SPAN_ID);
      new SpanId.Serializer().serialize(parentSpanId, writer);
    }
    if (sampled != null) {
      writer.name(JsonKeys.SAMPLED).value(sampled);
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

  public static final class Deserializer implements JsonDeserializer<SpanContext> {
    @SuppressWarnings("unchecked")
    @Override
    public @NotNull SpanContext deserialize(
        @NotNull JsonObjectReader reader, @NotNull ILogger logger) throws Exception {
      reader.beginObject();
      SentryId traceId = null;
      SpanId spanId = null;
      SpanId parentSpanId = null;
      Boolean sampled = null;
      String op = null;
      String description = null;
      SpanStatus status = null;
      Map<String, String> tags = null;

      Map<String, Object> unknown = null;
      do {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.TRACE_ID:
            traceId = new SentryId.Deserializer().deserialize(reader);
            break;
          case JsonKeys.SPAN_ID:
            spanId = new SpanId.Deserializer().deserialize(reader);
            break;
          case JsonKeys.PARENT_SPAN_ID:
            parentSpanId = new SpanId.Deserializer().deserialize(reader);
            break;
          case JsonKeys.SAMPLED:
            sampled = reader.nextBooleanOrNull();
            break;
          case JsonKeys.OP:
            op = reader.nextString();
            break;
          case JsonKeys.DESCRIPTION:
            description = reader.nextString();
            break;
          case JsonKeys.STATUS:
            status = reader.nextSpanStatusOrNull();
            break;
          case JsonKeys.TAGS:
            tags = (Map<String, String>) reader.nextObjectOrNull();
            break;
          default:
            if (unknown == null) {
              unknown = new ConcurrentHashMap<>();
            }
            reader.nextUnknown(logger, unknown, nextName);
            break;
        }
      } while (reader.hasNext());

      if (traceId == null) {
        String message = "Missing required field \"" + JsonKeys.TRACE_ID + "\"";
        Exception exception = new IllegalStateException(message);
        logger.log(SentryLevel.ERROR, message, exception);
        throw exception;
      }

      if (spanId == null) {
        String message = "Missing required field \"" + JsonKeys.SPAN_ID + "\"";
        Exception exception = new IllegalStateException(message);
        logger.log(SentryLevel.ERROR, message, exception);
        throw exception;
      }

      if (op == null) {
        String message = "Missing required field \"" + JsonKeys.OP + "\"";
        Exception exception = new IllegalStateException(message);
        logger.log(SentryLevel.ERROR, message, exception);
        throw exception;
      }

      SpanContext spanContext = new SpanContext(traceId, spanId, op, parentSpanId, sampled);
      spanContext.setDescription(description);
      spanContext.setStatus(status);
      if (tags != null) {
        spanContext.tags = tags;
      }
      spanContext.setUnknown(unknown);
      reader.endObject();
      return spanContext;
    }
  }
}
