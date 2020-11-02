package io.sentry;

import io.sentry.protocol.SentryId;
import org.jetbrains.annotations.NotNull;

/** Represents HTTP header "sentry-trace". */
public final class SentryTraceHeader {
  public static final String SENTRY_TRACE_HEADER = "sentry-trace";

  private final @NotNull SentryId traceId;
  private final @NotNull SpanId spanId;

  public SentryTraceHeader(final @NotNull SentryId traceId, final @NotNull SpanId spanId) {
    this.traceId = traceId;
    this.spanId = spanId;
  }

  public SentryTraceHeader(final @NotNull String value) throws InvalidSentryTraceHeaderException {
    final String[] parts = value.split("-", -1);
    if (parts.length < 2) {
      throw new InvalidSentryTraceHeaderException(value);
    }
    this.traceId = new SentryId(parts[0]);
    this.spanId = new SpanId(parts[1]);
  }

  public @NotNull String getName() {
    return SENTRY_TRACE_HEADER;
  }

  public @NotNull String getValue() {
    return String.format("%s-%s", traceId, spanId);
  }

  public @NotNull SentryId getTraceId() {
    return traceId;
  }

  public @NotNull SpanId getSpanId() {
    return spanId;
  }
}
