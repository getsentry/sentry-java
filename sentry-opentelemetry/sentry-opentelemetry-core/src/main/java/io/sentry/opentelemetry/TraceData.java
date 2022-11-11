package io.sentry.opentelemetry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class TraceData {

  private final @NotNull String traceId;
  private final @NotNull String spanId;
  private final @Nullable String parentSpanId;
  // TODO parentSampled
  // TODO baggage

  public TraceData(
      @NotNull final String traceId,
      @NotNull final String spanId,
      @Nullable final String parentSpanId) {
    this.traceId = traceId;
    this.spanId = spanId;
    this.parentSpanId = parentSpanId;
  }

  public @NotNull String getTraceId() {
    return traceId;
  }

  public @NotNull String getSpanId() {
    return spanId;
  }

  public @Nullable String getParentSpanId() {
    return parentSpanId;
  }
}
