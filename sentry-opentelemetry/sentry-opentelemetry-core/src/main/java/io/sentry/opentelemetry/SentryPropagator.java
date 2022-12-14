package io.sentry.opentelemetry;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.sentry.Baggage;
import io.sentry.BaggageHeader;
import io.sentry.ISpan;
import io.sentry.SentrySpanStorage;
import io.sentry.SentryTraceHeader;
import io.sentry.exception.InvalidSentryTraceHeaderException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SentryPropagator implements TextMapPropagator {

  private static final @NotNull List<String> FIELDS =
      Arrays.asList(SentryTraceHeader.SENTRY_TRACE_HEADER, BaggageHeader.BAGGAGE_HEADER);
  private final @NotNull SentrySpanStorage spanStorage = SentrySpanStorage.getInstance();

  @Override
  public Collection<String> fields() {
    return FIELDS;
  }

  @Override
  public <C> void inject(final Context context, final C carrier, final TextMapSetter<C> setter) {
    final @NotNull Span otelSpan = Span.fromContext(context);
    final @NotNull SpanContext otelSpanContext = otelSpan.getSpanContext();
    if (!otelSpanContext.isValid()) {
      return;
    }
    final @Nullable ISpan sentrySpan = spanStorage.get(otelSpanContext.getSpanId());
    if (sentrySpan == null || sentrySpan.isNoOp()) {
      return;
    }

    final @NotNull SentryTraceHeader sentryTraceHeader = sentrySpan.toSentryTrace();
    setter.set(carrier, sentryTraceHeader.getName(), sentryTraceHeader.getValue());
    final @Nullable BaggageHeader baggageHeader =
        sentrySpan.toBaggageHeader(Collections.emptyList());
    if (baggageHeader != null) {
      setter.set(carrier, baggageHeader.getName(), baggageHeader.getValue());
    }
  }

  @Override
  public <C> Context extract(
      final Context context, final C carrier, final TextMapGetter<C> getter) {
    final @Nullable String sentryTraceString =
        getter.get(carrier, SentryTraceHeader.SENTRY_TRACE_HEADER);
    if (sentryTraceString == null) {
      return context;
    }

    try {
      SentryTraceHeader sentryTraceHeader = new SentryTraceHeader(sentryTraceString);

      SpanContext otelSpanContext =
          SpanContext.createFromRemoteParent(
              sentryTraceHeader.getTraceId().toString(),
              sentryTraceHeader.getSpanId().toString(),
              TraceFlags.getSampled(),
              TraceState.getDefault());

      @NotNull
      Context modifiedContext = context.with(SentryOtelKeys.SENTRY_TRACE_KEY, sentryTraceHeader);

      final @Nullable String baggageString = getter.get(carrier, BaggageHeader.BAGGAGE_HEADER);
      Baggage baggage = Baggage.fromHeader(baggageString);
      modifiedContext = modifiedContext.with(SentryOtelKeys.SENTRY_BAGGAGE_KEY, baggage);

      Span wrappedSpan = Span.wrap(otelSpanContext);
      modifiedContext = modifiedContext.with(wrappedSpan);

      return modifiedContext;
    } catch (InvalidSentryTraceHeaderException e) {
      return context;
    }
  }
}
