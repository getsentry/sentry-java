package io.sentry;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.protocol.SentryId;
import io.sentry.util.CollectionUtils;
import io.sentry.util.Objects;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

@Open
public class SpanContext {
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
}
