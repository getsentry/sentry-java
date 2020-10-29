package io.sentry;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.protocol.SentryId;
import io.sentry.util.Objects;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Open
public class TraceContext {
  public static final String TYPE = "trace";

  /** Determines which trace the Span belongs to. */
  private final @NotNull SentryId traceId;

  /** Span id. */
  private final @NotNull SpanId spanId;

  /** Id of a parent span. */
  private final @Nullable SpanId parentSpanId;

  /** Short code identifying the type of operation the span is measuring. */
  protected @Nullable String op;

  /**
   * Longer description of the span's operation, which uniquely identifies the span but is
   * consistent across instances of the span.
   */
  protected @Nullable String description;

  /** Describes the status of the Transaction. */
  protected @Nullable SpanStatus status;

  /** A map or list of tags for this event. Each tag must be less than 200 characters. */
  protected @Nullable Map<String, String> tags;

  public TraceContext() {
    this(new SentryId(), new SpanId(), null);
  }

  public TraceContext(
      final @NotNull SentryId traceId,
      final @NotNull SpanId spanId,
      final @Nullable SpanId parentSpanId) {
    this.traceId = Objects.requireNonNull(traceId, "traceId is required");
    this.spanId = Objects.requireNonNull(spanId, "spanId is required");
    this.parentSpanId = parentSpanId;
  }

  public @NotNull String toSentryTrace() {
    return String.format("%s-%s", traceId, spanId);
  }

  public void setOp(@Nullable String op) {
    this.op = op;
  }

  public void setTag(final @NotNull String name, final @NotNull String value) {
    Objects.requireNonNull(name, "name is required");
    Objects.requireNonNull(value, "value is required");

    if (this.tags == null) {
      this.tags = new ConcurrentHashMap<>();
    }
    this.tags.put(name, value);
  }

  public void setDescription(@Nullable String description) {
    this.description = description;
  }

  public void setStatus(@Nullable SpanStatus status) {
    this.status = status;
  }

  @NotNull
  SentryId getTraceId() {
    return traceId;
  }

  @NotNull
  SpanId getSpanId() {
    return spanId;
  }

  @Nullable
  SpanId getParentSpanId() {
    return parentSpanId;
  }

  public @Nullable String getOp() {
    return op;
  }

  public @Nullable String getDescription() {
    return description;
  }

  public @Nullable SpanStatus getStatus() {
    return status;
  }

  public @Nullable Map<String, String> getTags() {
    return tags;
  }
}
