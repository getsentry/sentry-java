package io.sentry.opentelemetry;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.TraceId;
import io.sentry.EventProcessor;
import io.sentry.Hint;
import io.sentry.HubAdapter;
import io.sentry.ISpan;
import io.sentry.Instrumenter;
import io.sentry.SentryEvent;
import io.sentry.SentrySpanStorage;
import io.sentry.SpanContext;
import io.sentry.protocol.SentryId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class OpenTelemetryLinkErrorEventProcessor implements EventProcessor {

  private final @NotNull SentrySpanStorage spanStorage = SentrySpanStorage.getInstance();

  @Override
  public @Nullable SentryEvent process(final @NotNull SentryEvent event, final @NotNull Hint hint) {
    if (Instrumenter.OTEL.equals(HubAdapter.getInstance().getOptions().getInstrumenter())) {
      @NotNull final Span otelSpan = Span.current();
      @NotNull final String traceId = otelSpan.getSpanContext().getTraceId();
      @NotNull final String spanId = otelSpan.getSpanContext().getSpanId();

      if (TraceId.isValid(traceId) && SpanId.isValid(spanId)) {
        final @Nullable ISpan sentrySpan = spanStorage.get(spanId);
        if (sentrySpan != null) {
          final @NotNull SpanContext sentrySpanSpanContext = sentrySpan.getSpanContext();
          final @NotNull String operation = sentrySpanSpanContext.getOperation();
          final @Nullable io.sentry.SpanId parentSpanId = sentrySpanSpanContext.getParentSpanId();
          final @NotNull SpanContext spanContext =
              new SpanContext(
                  new SentryId(traceId),
                  new io.sentry.SpanId(spanId),
                  operation,
                  parentSpanId,
                  null);

          event.getContexts().setTrace(spanContext);
        }
      }
    }

    return event;
  }
}
