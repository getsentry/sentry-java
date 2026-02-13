package io.sentry.opentelemetry.otlp;

import static io.sentry.SentryTraceHeader.SENTRY_TRACE_HEADER;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.sentry.Baggage;
import io.sentry.BaggageHeader;
import io.sentry.IScopes;
import io.sentry.ScopesAdapter;
import io.sentry.SentryLevel;
import io.sentry.SentryTraceHeader;
import io.sentry.exception.InvalidSentryTraceHeaderException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class OpenTelemetryOtlpPropagator implements TextMapPropagator {

  private static final @NotNull List<String> FIELDS =
      Arrays.asList(SENTRY_TRACE_HEADER, BaggageHeader.BAGGAGE_HEADER);

  public static final @NotNull ContextKey<Baggage> SENTRY_BAGGAGE_KEY =
      ContextKey.named("sentry.baggage");
  private final @NotNull IScopes scopes;

  public OpenTelemetryOtlpPropagator() {
    this(ScopesAdapter.getInstance());
  }

  OpenTelemetryOtlpPropagator(final @NotNull IScopes scopes) {
    this.scopes = scopes;
  }

  @Override
  public Collection<String> fields() {
    return FIELDS;
  }

  @Override
  public <C> void inject(final Context context, final C carrier, final TextMapSetter<C> setter) {
    final @NotNull Span otelSpan = Span.fromContext(context);
    final @NotNull SpanContext otelSpanContext = otelSpan.getSpanContext();
    if (!otelSpanContext.isValid()) {
      scopes
          .getOptions()
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Not injecting Sentry tracing information for invalid OpenTelemetry span.");
      return;
    }

    setter.set(
        carrier,
        SENTRY_TRACE_HEADER,
        otelSpanContext.getTraceId()
            + "-"
            + otelSpanContext.getSpanId()
            + "-"
            + (otelSpanContext.isSampled() ? "1" : "0"));

    final @Nullable Baggage baggage = context.get(SENTRY_BAGGAGE_KEY);
    if (baggage != null) {
      setter.set(carrier, BaggageHeader.BAGGAGE_HEADER, baggage.toHeaderString(null));
    }
  }

  @Override
  public <C> Context extract(
      final Context context, final C carrier, final TextMapGetter<C> getter) {
    final @Nullable String sentryTraceString = getter.get(carrier, SENTRY_TRACE_HEADER);
    if (sentryTraceString == null) {
      return context;
    }

    try {
      SentryTraceHeader sentryTraceHeader = new SentryTraceHeader(sentryTraceString);

      final @Nullable String baggageString = getter.get(carrier, BaggageHeader.BAGGAGE_HEADER);
      final Baggage baggage = Baggage.fromHeader(baggageString);
      final @NotNull TraceState traceState = TraceState.getDefault();

      SpanContext otelSpanContext =
          SpanContext.createFromRemoteParent(
              sentryTraceHeader.getTraceId().toString(),
              sentryTraceHeader.getSpanId().toString(),
              TraceFlags.getSampled(),
              traceState);

      Span wrappedSpan = Span.wrap(otelSpanContext);

      final @NotNull Context modifiedContext =
          context.with(wrappedSpan).with(SENTRY_BAGGAGE_KEY, baggage);

      scopes
          .getOptions()
          .getLogger()
          .log(SentryLevel.DEBUG, "Continuing Sentry trace %s", sentryTraceHeader.getTraceId());

      return modifiedContext;
    } catch (InvalidSentryTraceHeaderException e) {
      scopes
          .getOptions()
          .getLogger()
          .log(
              SentryLevel.ERROR,
              "Unable to extract Sentry tracing information from invalid header.",
              e);
      return context;
    }
  }
}
