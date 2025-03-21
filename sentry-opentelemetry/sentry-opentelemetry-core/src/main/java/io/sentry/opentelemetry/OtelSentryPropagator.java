package io.sentry.opentelemetry;

import static io.sentry.opentelemetry.SentryOtelKeys.SENTRY_SCOPES_KEY;

import io.opentelemetry.api.common.Attributes;
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
import io.sentry.IScopes;
import io.sentry.ScopesAdapter;
import io.sentry.Sentry;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.SentryTraceHeader;
import io.sentry.exception.InvalidSentryTraceHeaderException;
import io.sentry.util.TracingUtils;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class OtelSentryPropagator implements TextMapPropagator {

  private static final @NotNull List<String> FIELDS =
      Arrays.asList(SentryTraceHeader.SENTRY_TRACE_HEADER, BaggageHeader.BAGGAGE_HEADER);
  private final @NotNull SentryWeakSpanStorage spanStorage = SentryWeakSpanStorage.getInstance();
  private final @NotNull IScopes scopes;
  private final @NotNull OpenTelemetryAttributesExtractor attributesExtractor =
      new OpenTelemetryAttributesExtractor();

  public OtelSentryPropagator() {
    this(ScopesAdapter.getInstance());
  }

  OtelSentryPropagator(final @NotNull IScopes scopes) {
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

    final @Nullable IOtelSpanWrapper sentrySpan = spanStorage.getSentrySpan(otelSpanContext);
    if (sentrySpan == null || sentrySpan.isNoOp()) {
      scopes
          .getOptions()
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Not injecting Sentry tracing information for span %s as no Sentry span has been found or it is a NoOp (trace %s). This might simply mean this is a request to Sentry.",
              otelSpanContext.getSpanId(),
              otelSpanContext.getTraceId());
      return;
    }

    final @Nullable String url = getUrl(sentrySpan, scopes.getOptions());
    final @Nullable TracingUtils.TracingHeaders tracingHeaders =
        url == null
            ? TracingUtils.trace(scopes, Collections.emptyList(), sentrySpan)
            : TracingUtils.traceIfAllowed(scopes, url, Collections.emptyList(), sentrySpan);

    if (tracingHeaders != null) {
      final @NotNull SentryTraceHeader sentryTraceHeader = tracingHeaders.getSentryTraceHeader();
      setter.set(carrier, sentryTraceHeader.getName(), sentryTraceHeader.getValue());
      final @Nullable BaggageHeader baggageHeader = tracingHeaders.getBaggageHeader();
      if (baggageHeader != null) {
        setter.set(carrier, baggageHeader.getName(), baggageHeader.getValue());
      }
    }
  }

  private @Nullable String getUrl(
      final @NotNull IOtelSpanWrapper sentrySpan, final @NotNull SentryOptions options) {
    final @Nullable Attributes attributes = sentrySpan.getOpenTelemetrySpanAttributes();
    if (attributes == null) {
      return null;
    }
    return attributesExtractor.extractUrl(attributes, options);
  }

  @Override
  public <C> Context extract(
      final Context context, final C carrier, final TextMapGetter<C> getter) {
    final @Nullable IScopes scopesFromParentContext = context.get(SENTRY_SCOPES_KEY);
    final @NotNull IScopes scopesToUse =
        scopesFromParentContext != null
            ? scopesFromParentContext.forkedScopes("propagator")
            : Sentry.forkedRootScopes("propagator");

    final @Nullable String sentryTraceString =
        getter.get(carrier, SentryTraceHeader.SENTRY_TRACE_HEADER);
    if (sentryTraceString == null) {
      return context.with(SENTRY_SCOPES_KEY, scopesToUse);
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
          context
              .with(wrappedSpan)
              .with(SENTRY_SCOPES_KEY, scopesToUse)
              .with(SentryOtelKeys.SENTRY_TRACE_KEY, sentryTraceHeader)
              .with(SentryOtelKeys.SENTRY_BAGGAGE_KEY, baggage);

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
