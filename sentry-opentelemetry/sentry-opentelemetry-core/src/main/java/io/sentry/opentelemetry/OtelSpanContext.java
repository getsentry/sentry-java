package io.sentry.opentelemetry;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.sentry.Baggage;
import io.sentry.SpanContext;
import io.sentry.SpanId;
import io.sentry.SpanStatus;
import io.sentry.TracesSamplingDecision;
import io.sentry.protocol.SentryId;
import java.lang.ref.WeakReference;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class OtelSpanContext extends SpanContext {

  /**
   * OpenTelemetry span which this wrapper wraps. Needs to be referenced weakly as otherwise we'd
   * create a circular reference from {@link io.opentelemetry.sdk.trace.data.SpanData} to {@link
   * OtelSpanWrapper} and indirectly back to {@link io.opentelemetry.sdk.trace.data.SpanData} via
   * {@link Span}. Also see {@link SentryWeakSpanStorage}.
   */
  private final @NotNull WeakReference<ReadWriteSpan> span;

  public OtelSpanContext(
      final @NotNull ReadWriteSpan span,
      final @Nullable TracesSamplingDecision samplingDecision,
      final @Nullable IOtelSpanWrapper parentSpan,
      final @Nullable SpanId parentSpanId,
      final @Nullable Baggage baggage) {
    super(
        new SentryId(span.getSpanContext().getTraceId()),
        new SpanId(span.getSpanContext().getSpanId()),
        parentSpan == null ? parentSpanId : parentSpan.getSpanContext().getSpanId(),
        span.getName(),
        null,
        samplingDecision != null
            ? samplingDecision
            : (parentSpan == null ? null : parentSpan.getSamplingDecision()),
        null,
        null);
    this.span = new WeakReference<>(span);
    this.baggage = baggage;
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

  @Override
  public @NotNull String getOperation() {
    final @Nullable ReadWriteSpan otelSpan = span.get();
    if (otelSpan != null) {
      return otelSpan.getName();
    }
    return "<unlabeled span>";
  }

  @Override
  public void setOperation(@NotNull String operation) {
    final @Nullable ReadWriteSpan otelSpan = span.get();
    if (otelSpan != null) {
      otelSpan.updateName(operation);
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
