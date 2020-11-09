package io.sentry;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.protocol.SentryId;
import io.sentry.util.CollectionUtils;
import io.sentry.util.Objects;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Open
public class SpanContext implements Cloneable {
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

  /**
   * Creates {@link SpanContext} from sentry-trace header.
   *
   * @param sentryTrace - the sentry-trace header
   * @return the transaction contexts
   */
  public static @NotNull SpanContext fromSentryTrace(final @NotNull SentryTraceHeader sentryTrace) {
    return new SpanContext(
        sentryTrace.getTraceId(), new SpanId(), sentryTrace.getSpanId(), sentryTrace.isSampled());
  }

  public SpanContext(@Nullable Boolean sampled) {
    this(new SentryId(), new SpanId(), null, sampled);
  }

  /** Creates trace context with defered sampling decision. */
  public SpanContext() {
    this(new SentryId(), new SpanId(), null, null);
  }

  public SpanContext(
      final @NotNull SentryId traceId,
      final @NotNull SpanId spanId,
      final @Nullable SpanId parentSpanId,
      final @Nullable Boolean sampled) {
    this.traceId = Objects.requireNonNull(traceId, "traceId is required");
    this.spanId = Objects.requireNonNull(spanId, "spanId is required");
    this.parentSpanId = parentSpanId;
    this.sampled = sampled;
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

  public @Nullable Boolean getSampled() {
    return sampled;
  }

  public void setSampled(Boolean sampled) {
    this.sampled = sampled;
  }

  @Override
  public SpanContext clone() throws CloneNotSupportedException {
    final SpanContext clone = (SpanContext) super.clone();
    clone.tags = CollectionUtils.shallowCopy(tags);
    return clone;
  }
}
