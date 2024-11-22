package io.sentry.opentelemetry;

import static io.sentry.opentelemetry.InternalSemanticAttributes.IS_REMOTE_PARENT;
import static io.sentry.opentelemetry.SentryOtelKeys.SENTRY_SCOPES_KEY;

import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.sentry.Baggage;
import io.sentry.IScopes;
import io.sentry.PropagationContext;
import io.sentry.ScopesAdapter;
import io.sentry.Sentry;
import io.sentry.SentryDate;
import io.sentry.SentryLevel;
import io.sentry.SentryLongDate;
import io.sentry.SentryTraceHeader;
import io.sentry.SpanId;
import io.sentry.TracesSamplingDecision;
import io.sentry.protocol.SentryId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class OtelSentrySpanProcessor implements SpanProcessor {
  private final @NotNull SentryWeakSpanStorage spanStorage = SentryWeakSpanStorage.getInstance();
  private final @NotNull IScopes scopes;

  public OtelSentrySpanProcessor() {
    this(ScopesAdapter.getInstance());
  }

  OtelSentrySpanProcessor(final @NotNull IScopes scopes) {
    this.scopes = scopes;
  }

  @Override
  public void onStart(final @NotNull Context parentContext, final @NotNull ReadWriteSpan otelSpan) {
    if (!ensurePrerequisites(otelSpan)) {
      return;
    }

    final @Nullable IScopes scopesFromContext = parentContext.get(SENTRY_SCOPES_KEY);
    final @NotNull IScopes scopes =
        scopesFromContext != null
            ? scopesFromContext.forkedCurrentScope("spanprocessor")
            : Sentry.forkedRootScopes("spanprocessor");

    final @Nullable IOtelSpanWrapper sentryParentSpan =
        spanStorage.getSentrySpan(otelSpan.getParentSpanContext());
    @NotNull
    TracesSamplingDecision samplingDecision =
        OtelSamplingUtil.extractSamplingDecisionOrDefault(otelSpan.toSpanData().getAttributes());
    @Nullable Baggage baggage = null;
    @Nullable SpanId sentryParentSpanId = null;
    otelSpan.setAttribute(IS_REMOTE_PARENT, otelSpan.getParentSpanContext().isRemote());
    if (sentryParentSpan == null) {
      final @NotNull String traceId = otelSpan.getSpanContext().getTraceId();
      final @NotNull String spanId = otelSpan.getSpanContext().getSpanId();
      final @NotNull SpanId sentrySpanId = new SpanId(spanId);
      final @NotNull String parentSpanId = otelSpan.getParentSpanContext().getSpanId();
      sentryParentSpanId =
          io.opentelemetry.api.trace.SpanId.isValid(parentSpanId) ? new SpanId(parentSpanId) : null;

      @Nullable
      SentryTraceHeader sentryTraceHeader = parentContext.get(SentryOtelKeys.SENTRY_TRACE_KEY);
      @Nullable Baggage baggageFromContext = parentContext.get(SentryOtelKeys.SENTRY_BAGGAGE_KEY);
      if (sentryTraceHeader != null) {
        baggage = baggageFromContext;
      }

      final @Nullable Boolean baggageMutable =
          otelSpan.getAttribute(InternalSemanticAttributes.BAGGAGE_MUTABLE);
      final @Nullable String baggageString =
          otelSpan.getAttribute(InternalSemanticAttributes.BAGGAGE);
      if (baggageString != null) {
        baggage = Baggage.fromHeader(baggageString);
        if (baggageMutable == true) {
          baggage.freeze();
        }
      }

      final boolean sampled =
          samplingDecision != null
              ? samplingDecision.getSampled()
              : otelSpan.getSpanContext().isSampled();

      final @NotNull PropagationContext propagationContext =
          sentryTraceHeader == null
              ? new PropagationContext(
                  new SentryId(traceId), sentrySpanId, sentryParentSpanId, baggage, sampled)
              : PropagationContext.fromHeaders(sentryTraceHeader, baggage, sentrySpanId);

      updatePropagationContext(scopes, propagationContext);
    }

    final @NotNull SpanContext spanContext = otelSpan.getSpanContext();
    final @NotNull SentryDate startTimestamp =
        new SentryLongDate(otelSpan.toSpanData().getStartEpochNanos());
    final @NotNull IOtelSpanWrapper sentrySpan =
        new OtelSpanWrapper(
            otelSpan,
            scopes,
            startTimestamp,
            samplingDecision,
            sentryParentSpan,
            sentryParentSpanId,
            baggage);
    sentrySpan.getSpanContext().setOrigin(SentrySpanExporter.TRACE_ORIGIN);
    spanStorage.storeSentrySpan(spanContext, sentrySpan);
  }

  private static void updatePropagationContext(
      IScopes scopes, PropagationContext propagationContext) {
    scopes.configureScope(
        scope -> {
          scope.withPropagationContext(
              oldPropagationContext -> {
                scope.setPropagationContext(propagationContext);
              });
        });
  }

  @Override
  public boolean isStartRequired() {
    return true;
  }

  @Override
  public void onEnd(final @NotNull ReadableSpan spanBeingEnded) {
    final @Nullable IOtelSpanWrapper sentrySpan =
        spanStorage.getSentrySpan(spanBeingEnded.getSpanContext());
    if (sentrySpan != null) {
      final @NotNull SentryDate finishDate =
          new SentryLongDate(spanBeingEnded.toSpanData().getEndEpochNanos());
      sentrySpan.updateEndDate(finishDate);
    }
  }

  @Override
  public boolean isEndRequired() {
    return true;
  }

  private boolean ensurePrerequisites(final @NotNull ReadableSpan otelSpan) {
    if (!hasSentryBeenInitialized()) {
      scopes
          .getOptions()
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Not forwarding OpenTelemetry span to Sentry as Sentry has not yet been initialized.");
      return false;
    }

    final @NotNull SpanContext otelSpanContext = otelSpan.getSpanContext();
    if (!otelSpanContext.isValid()) {
      scopes
          .getOptions()
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Not forwarding OpenTelemetry span to Sentry as the span is invalid.");
      return false;
    }

    return true;
  }

  private boolean hasSentryBeenInitialized() {
    return scopes.isEnabled();
  }
}
