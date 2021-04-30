package io.sentry.protocol;

import io.sentry.Span;
import io.sentry.SpanId;
import io.sentry.SpanStatus;
import io.sentry.util.CollectionUtils;
import io.sentry.util.Objects;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SentrySpan {
  private final @NotNull Date startTimestamp;
  private final @Nullable Date timestamp;
  private final @NotNull SentryId traceId;
  private final @NotNull SpanId spanId;
  private final @Nullable SpanId parentSpanId;
  private final @NotNull String op;
  private final @Nullable String description;
  private final @Nullable SpanStatus status;
  private final @NotNull Map<String, String> tags;

  public SentrySpan(final @NotNull Span span) {
    Objects.requireNonNull(span, "span is required");
    this.description = span.getDescription();
    this.op = span.getOperation();
    this.spanId = span.getSpanId();
    this.parentSpanId = span.getParentSpanId();
    this.traceId = span.getTraceId();
    this.status = span.getStatus();
    final Map<String, String> tagsCopy = CollectionUtils.shallowCopy(span.getTags());
    this.tags = tagsCopy != null ? tagsCopy : new ConcurrentHashMap<>();
    this.timestamp = span.getTimestamp();
    this.startTimestamp = span.getStartTimestamp();
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
}
