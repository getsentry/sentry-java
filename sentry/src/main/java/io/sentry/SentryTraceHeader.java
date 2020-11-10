package io.sentry;

import io.sentry.protocol.SentryId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Represents HTTP header "sentry-trace". */
public final class SentryTraceHeader {
  public static final String SENTRY_TRACE_HEADER = "sentry-trace";

  private final @NotNull SentryId traceId;
  private final @NotNull SpanId spanId;
  private final @Nullable Boolean sampled;

  public SentryTraceHeader(
      final @NotNull SentryId traceId,
      final @NotNull SpanId spanId,
      final @Nullable Boolean sampled) {
    this.traceId = traceId;
    this.spanId = spanId;
    this.sampled = sampled;
  }

  public SentryTraceHeader(final @NotNull String value) throws InvalidSentryTraceHeaderException {
    final String[] parts = value.split("-", -1);
    if (parts.length < 2) {
      throw new InvalidSentryTraceHeaderException(value);
    } else if (parts.length == 3) {
      this.sampled = "1".equals(parts[2]);
    } else {
      this.sampled = null;
    }
    this.traceId = new SentryId(parts[0]);
    this.spanId = new SpanId(parts[1]);
  }

  public @NotNull String getName() {
    return SENTRY_TRACE_HEADER;
  }

  public @NotNull String getValue() {
    if (sampled != null) {
      return String.format("%s-%s-%s", traceId, spanId, sampled ? "1" : "0");
    } else {
      return String.format("%s-%s", traceId, spanId);
    }
  }

  public @NotNull SentryId getTraceId() {
    return traceId;
  }

  public @NotNull SpanId getSpanId() {
    return spanId;
  }

  public @Nullable Boolean isSampled() {
    return sampled;
  }
}
