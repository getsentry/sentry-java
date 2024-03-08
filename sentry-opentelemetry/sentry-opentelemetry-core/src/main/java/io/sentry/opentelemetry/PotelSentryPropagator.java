package io.sentry.opentelemetry;

import static io.sentry.opentelemetry.SentryOtelKeys.SENTRY_SCOPES_KEY;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.sentry.BaggageHeader;
import io.sentry.HubAdapter;
import io.sentry.IHub;
import io.sentry.PropagationContext;
import io.sentry.Scopes;
import io.sentry.SentryLevel;
import io.sentry.SentryTraceHeader;
import io.sentry.exception.InvalidSentryTraceHeaderException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PotelSentryPropagator implements TextMapPropagator {

  private static final @NotNull List<String> FIELDS =
      Arrays.asList(SentryTraceHeader.SENTRY_TRACE_HEADER, BaggageHeader.BAGGAGE_HEADER);
  //  private final @NotNull SentryWeakSpanStorage spanStorage =
  // SentryWeakSpanStorage.getInstance();
  private final @NotNull IHub hub;

  public PotelSentryPropagator() {
    this(HubAdapter.getInstance());
  }

  PotelSentryPropagator(final @NotNull IHub hub) {
    this.hub = hub;
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
      hub.getOptions()
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Not injecting Sentry tracing information for invalid OpenTelemetry span.");
      return;
    }

    /**
     * TODO
     *
     * <p>maybe it could work like this:
     *
     * <p>getIsolationScope() check if there's a PropagationContext on there and use that for
     * generating headers and freezing
     *
     * <p>if that's not there check Context for data and attach headers
     */

    // TODO: inject from OTEL SpanContext and TraceState
    System.out.println("TODO");
    // TODO how to inject?
    //    final @Nullable ISpan sentrySpan = spanStorage.get(otelSpanContext.getSpanId());
    //    if (sentrySpan == null || sentrySpan.isNoOp()) {
    //      hub.getOptions()
    //          .getLogger()
    //          .log(
    //              SentryLevel.DEBUG,
    //              "Not injecting Sentry tracing information for span %s as no Sentry span has been
    // found or it is a NoOp (trace %s). This might simply mean this is a request to Sentry.",
    //              otelSpanContext.getSpanId(),
    //              otelSpanContext.getTraceId());
    //      return;
    //    }
    //
    //    final @NotNull SentryTraceHeader sentryTraceHeader = sentrySpan.toSentryTrace();
    //    setter.set(carrier, sentryTraceHeader.getName(), sentryTraceHeader.getValue());
    //    final @Nullable BaggageHeader baggageHeader =
    //        sentrySpan.toBaggageHeader(Collections.emptyList());
    //    if (baggageHeader != null) {
    //      setter.set(carrier, baggageHeader.getName(), baggageHeader.getValue());
    //    }
  }

  @Override
  public <C> Context extract(
      final Context context, final C carrier, final TextMapGetter<C> getter) {
    final @Nullable Scopes scopesFromParentContext = context.get(SENTRY_SCOPES_KEY);
    final @NotNull Scopes scopes =
        scopesFromParentContext != null
            ? scopesFromParentContext.forkedScopes("propagator")
            : Scopes.forkedRoots("propagator");

    final @Nullable String sentryTraceString =
        getter.get(carrier, SentryTraceHeader.SENTRY_TRACE_HEADER);
    if (sentryTraceString == null) {

      final @NotNull Context modifiedContext = context.with(SENTRY_SCOPES_KEY, scopes);
      //      return context.with(SENTRY_SCOPES_KEY, scopes);
      return modifiedContext;
    }
    //    else {
    //      // TODO clean up code here
    //      // TODO should we rely on OTEL trace/span ids here?
    //      scopes.getIsolationScope().setPropagationContext(new PropagationContext());
    //    }

    try {
      SentryTraceHeader sentryTraceHeader = new SentryTraceHeader(sentryTraceString);

      final @Nullable String baggageString = getter.get(carrier, BaggageHeader.BAGGAGE_HEADER);
      //      Baggage baggage = Baggage.fromHeader(baggageString);

      //      final @NotNull TraceState traceState = TraceState.builder().put("todo.dsc",
      // baggage.).build();
      final @NotNull TraceState traceState = TraceState.getDefault();

      SpanContext otelSpanContext =
          SpanContext.createFromRemoteParent(
              sentryTraceHeader.getTraceId().toString(),
              sentryTraceHeader.getSpanId().toString(),
              TraceFlags.getSampled(),
              traceState);

      Span wrappedSpan = Span.wrap(otelSpanContext);

      final @NotNull Context modifiedContext =
          context.with(wrappedSpan).with(SENTRY_SCOPES_KEY, scopes);

      hub.getOptions()
          .getLogger()
          .log(SentryLevel.DEBUG, "Continuing Sentry trace %s", sentryTraceHeader.getTraceId());

      final @NotNull PropagationContext propagationContext =
          PropagationContext.fromHeaders(
              hub.getOptions().getLogger(), sentryTraceString, baggageString);
      scopes.getIsolationScope().setPropagationContext(propagationContext);

      return modifiedContext;
    } catch (InvalidSentryTraceHeaderException e) {
      hub.getOptions()
          .getLogger()
          .log(
              SentryLevel.ERROR,
              "Unable to extract Sentry tracing information from invalid header.",
              e);
      return context;
    }
  }
}
