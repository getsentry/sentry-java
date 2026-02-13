package io.sentry.opentelemetry.otlp;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.TraceId;
import io.sentry.EventProcessor;
import io.sentry.Hint;
import io.sentry.IScopes;
import io.sentry.ScopesAdapter;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.SentryLogEvent;
import io.sentry.SentryMetricsEvent;
import io.sentry.SpanContext;
import io.sentry.protocol.SentryId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public final class OpenTelemetryOtlpEventProcessor implements EventProcessor {

  private final @NotNull IScopes scopes;

  public OpenTelemetryOtlpEventProcessor() {
    this(ScopesAdapter.getInstance());
  }

  @TestOnly
  OpenTelemetryOtlpEventProcessor(final @NotNull IScopes scopes) {
    this.scopes = scopes;
  }

  @Override
  public @Nullable SentryEvent process(final @NotNull SentryEvent event, final @NotNull Hint hint) {
    @NotNull final Span otelSpan = Span.current();
    @NotNull final String traceId = otelSpan.getSpanContext().getTraceId();
    @NotNull final String spanId = otelSpan.getSpanContext().getSpanId();

    if (TraceId.isValid(traceId) && SpanId.isValid(spanId)) {
      final @NotNull SpanContext spanContext =
          new SpanContext(
              new SentryId(traceId), new io.sentry.SpanId(spanId), "opentelemetry", null, null);

      event.getContexts().setTrace(spanContext);
      scopes
          .getOptions()
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Linking Sentry event %s to span %s created via OpenTelemetry (trace %s).",
              event.getEventId(),
              spanId,
              traceId);
    } else {
      scopes
          .getOptions()
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Not linking Sentry event %s to any transaction created via OpenTelemetry as traceId %s or spanId %s are invalid.",
              event.getEventId(),
              traceId,
              spanId);
    }

    return event;
  }

  @Override
  public @Nullable SentryLogEvent process(@NotNull SentryLogEvent event) {
    @NotNull final Span otelSpan = Span.current();
    @NotNull final String traceId = otelSpan.getSpanContext().getTraceId();
    @NotNull final String spanId = otelSpan.getSpanContext().getSpanId();

    if (TraceId.isValid(traceId) && SpanId.isValid(spanId)) {
      event.setTraceId(new SentryId(traceId));
      event.setSpanId(new io.sentry.SpanId(spanId));
    } else {
      scopes
          .getOptions()
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Not linking Sentry event to any transaction created via OpenTelemetry as traceId %s or spanId %s are invalid.",
              traceId,
              spanId);
    }

    return event;
  }

  @Override
  public @Nullable SentryMetricsEvent process(
      @NotNull SentryMetricsEvent event, @NotNull Hint hint) {
    @NotNull final Span otelSpan = Span.current();
    @NotNull final String traceId = otelSpan.getSpanContext().getTraceId();
    @NotNull final String spanId = otelSpan.getSpanContext().getSpanId();

    if (TraceId.isValid(traceId) && SpanId.isValid(spanId)) {
      event.setTraceId(new SentryId(traceId));
      event.setSpanId(new io.sentry.SpanId(spanId));
    } else {
      scopes
          .getOptions()
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Not linking Sentry event to any transaction created via OpenTelemetry as traceId %s or spanId %s are invalid.",
              traceId,
              spanId);
    }

    return event;
  }

  @Override
  public @Nullable Long getOrder() {
    return 6000L;
  }
}
