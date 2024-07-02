package io.sentry.opentelemetry;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.TraceId;
import io.sentry.EventProcessor;
import io.sentry.Hint;
import io.sentry.HubAdapter;
import io.sentry.IHub;
import io.sentry.ISpan;
import io.sentry.Instrumenter;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.SentrySpanStorage;
import io.sentry.SpanContext;
import io.sentry.protocol.SentryId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public final class OpenTelemetryLinkErrorEventProcessor implements EventProcessor {

  private final @NotNull IHub hub;
  private final @NotNull SentrySpanStorage spanStorage = SentrySpanStorage.getInstance();

  public OpenTelemetryLinkErrorEventProcessor() {
    this(HubAdapter.getInstance());
  }

  @TestOnly
  OpenTelemetryLinkErrorEventProcessor(final @NotNull IHub hub) {
    this.hub = hub;
  }

  @Override
  public @Nullable SentryEvent process(final @NotNull SentryEvent event, final @NotNull Hint hint) {
    final @NotNull Instrumenter instrumenter = hub.getOptions().getInstrumenter();
    if (Instrumenter.OTEL.equals(instrumenter)) {
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
          hub.getOptions()
              .getLogger()
              .log(
                  SentryLevel.DEBUG,
                  "Linking Sentry event %s to span %s created via OpenTelemetry (trace %s).",
                  event.getEventId(),
                  spanId,
                  traceId);
        } else {
          hub.getOptions()
              .getLogger()
              .log(
                  SentryLevel.DEBUG,
                  "Not linking Sentry event %s to any transaction created via OpenTelemetry as none has been found for span %s (trace %s).",
                  event.getEventId(),
                  spanId,
                  traceId);
        }
      } else {
        hub.getOptions()
            .getLogger()
            .log(
                SentryLevel.DEBUG,
                "Not linking Sentry event %s to any transaction created via OpenTelemetry as traceId %s or spanId %s are invalid.",
                event.getEventId(),
                traceId,
                spanId);
      }
    } else {
      hub.getOptions()
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Not linking Sentry event %s to any transaction created via OpenTelemetry as instrumenter is set to %s.",
              event.getEventId(),
              instrumenter);
    }

    return event;
  }
}
