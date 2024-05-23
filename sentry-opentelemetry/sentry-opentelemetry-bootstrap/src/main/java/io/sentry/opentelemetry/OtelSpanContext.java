package io.sentry.opentelemetry;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.sentry.SpanContext;
import io.sentry.SpanId;
import io.sentry.SpanStatus;
import io.sentry.protocol.SentryId;
import java.lang.ref.WeakReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class OtelSpanContext extends SpanContext {

  /**
   * OpenTelemetry span which this wrapper wraps. Needs to be referenced weakly as otherwise we'd
   * create a circular reference from {@link io.opentelemetry.sdk.trace.data.SpanData} to {@link
   * OtelSpanWrapper} and indirectly back to {@link io.opentelemetry.sdk.trace.data.SpanData} via
   * {@link Span}. Also see {@link SentryWeakSpanStorage}.
   */
  private final @NotNull WeakReference<ReadWriteSpan> span;

  public OtelSpanContext(final @NotNull ReadWriteSpan span, final @Nullable Span parentSpan) {
    // TODO [POTEL] tracesSamplingDecision
    super(
        new SentryId(span.getSpanContext().getTraceId()),
        new SpanId(span.getSpanContext().getSpanId()),
        parentSpan == null ? null : new SpanId(parentSpan.getSpanContext().getSpanId()),
        span.getName(),
        null,
        null,
        null,
        null);
    this.span = new WeakReference<>(span);
  }

  @Override
  public @Nullable SpanStatus getStatus() {
    final @Nullable ReadWriteSpan otelSpan = span.get();

    if (otelSpan != null) {
      final @NotNull StatusData otelStatus = otelSpan.toSpanData().getStatus();
      final @NotNull String otelStatusDescription = otelStatus.getDescription();
      if (otelStatusDescription.isEmpty()) {
        return otelStatusCodeFallback(otelStatus);
      }
      final @Nullable SpanStatus spanStatus = SpanStatus.fromApiNameSafely(otelStatusDescription);
      if (spanStatus == null) {
        return otelStatusCodeFallback(otelStatus);
      }
      return spanStatus;
    }

    return null;
  }

  @Override
  public void setStatus(@Nullable SpanStatus status) {
    if (status != null) {
      final @Nullable ReadWriteSpan otelSpan = span.get();
      if (otelSpan != null) {
        final @NotNull StatusCode statusCode = translateStatusCode(status);
        otelSpan.setStatus(statusCode, status.apiName());
      }
    }
  }

  private @Nullable SpanStatus otelStatusCodeFallback(final @NotNull StatusData otelStatus) {
    if (otelStatus.getStatusCode() == StatusCode.ERROR) {
      return SpanStatus.UNKNOWN_ERROR;
    } else if (otelStatus.getStatusCode() == StatusCode.OK) {
      return SpanStatus.OK;
    }
    return null;
  }

  private @NotNull StatusCode translateStatusCode(final @Nullable SpanStatus status) {
    if (status == null) {
      return StatusCode.UNSET;
    } else if (status == SpanStatus.OK) {
      return StatusCode.OK;
    } else {
      return StatusCode.ERROR;
    }
  }
}
