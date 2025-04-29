package io.sentry.opentelemetry;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public final class SentryOtelGlobalHubModeSpan implements Span {

  private @NotNull Span getOtelSpan() {
    final @Nullable IOtelSpanWrapper lastKnownUnfinishedRootSpan =
        SentryWeakSpanStorage.getInstance().getLastKnownUnfinishedRootSpan();
    if (lastKnownUnfinishedRootSpan != null) {
      final @Nullable Span openTelemetrySpan = lastKnownUnfinishedRootSpan.getOpenTelemetrySpan();
      if (openTelemetrySpan != null) {
        return openTelemetrySpan;
      }
    }

    return Span.getInvalid();
  }

  @Override
  public <T> Span setAttribute(AttributeKey<T> key, T value) {
    return getOtelSpan().setAttribute(key, value);
  }

  @Override
  public Span addEvent(String name, Attributes attributes) {
    return getOtelSpan().addEvent(name, attributes);
  }

  @Override
  public Span addEvent(String name, Attributes attributes, long timestamp, TimeUnit unit) {
    return getOtelSpan().addEvent(name, attributes, timestamp, unit);
  }

  @Override
  public Span setStatus(StatusCode statusCode, String description) {
    return getOtelSpan().setStatus(statusCode, description);
  }

  @Override
  public Span recordException(Throwable exception, Attributes additionalAttributes) {
    return getOtelSpan().recordException(exception, additionalAttributes);
  }

  @Override
  public Span updateName(String name) {
    return getOtelSpan().updateName(name);
  }

  @Override
  public void end() {
    getOtelSpan().end();
  }

  @Override
  public void end(long timestamp, TimeUnit unit) {
    getOtelSpan().end(timestamp, unit);
  }

  @Override
  public SpanContext getSpanContext() {
    return getOtelSpan().getSpanContext();
  }

  @Override
  public boolean isRecording() {
    return getOtelSpan().isRecording();
  }
}
