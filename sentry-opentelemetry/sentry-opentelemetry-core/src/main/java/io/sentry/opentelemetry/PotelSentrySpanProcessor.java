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
import io.sentry.SamplingContext;
import io.sentry.ScopesAdapter;
import io.sentry.Sentry;
import io.sentry.SentryDate;
import io.sentry.SentryLevel;
import io.sentry.SentryLongDate;
import io.sentry.SentryTraceHeader;
import io.sentry.SpanId;
import io.sentry.TracesSampler;
import io.sentry.TracesSamplingDecision;
import io.sentry.TransactionContext;
import io.sentry.protocol.SentryId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PotelSentrySpanProcessor implements SpanProcessor {
  private final @NotNull SentryWeakSpanStorage spanStorage = SentryWeakSpanStorage.getInstance();
  private final @NotNull IScopes scopes;

  private final @NotNull TracesSampler tracesSampler;

  public PotelSentrySpanProcessor() {
    this(ScopesAdapter.getInstance());
  }

  PotelSentrySpanProcessor(final @NotNull IScopes scopes) {
    this.scopes = scopes;
    this.tracesSampler = new TracesSampler(scopes.getOptions());
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

    final @Nullable OtelSpanWrapper sentryParentSpan =
        spanStorage.getSentrySpan(otelSpan.getParentSpanContext());
    @Nullable TracesSamplingDecision samplingDecision = null;
    // TODO [POTEL] baggage from propagator should be honored
    @Nullable Baggage baggage = null;
    otelSpan.setAttribute(IS_REMOTE_PARENT, otelSpan.getParentSpanContext().isRemote());
    if (sentryParentSpan == null) {
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
      final @Nullable Boolean sampled = otelSpan.getAttribute(InternalSemanticAttributes.SAMPLED);
      final @Nullable Double sampleRate =
          otelSpan.getAttribute(InternalSemanticAttributes.SAMPLE_RATE);
      final @Nullable Boolean profileSampled =
          otelSpan.getAttribute(InternalSemanticAttributes.PROFILE_SAMPLED);
      final @Nullable Double profileSampleRate =
          otelSpan.getAttribute(InternalSemanticAttributes.PROFILE_SAMPLE_RATE);
      if (sampled != null) {
        // span created by Sentry API

        final @NotNull String traceId = otelSpan.getSpanContext().getTraceId();
        final @NotNull String spanId = otelSpan.getSpanContext().getSpanId();
        // TODO [POTEL] parent span id could be invalid
        final @NotNull String parentSpanId = otelSpan.getParentSpanContext().getSpanId();

        final @NotNull PropagationContext propagationContext =
            new PropagationContext(
                new SentryId(traceId),
                new SpanId(spanId),
                new SpanId(parentSpanId),
                baggage,
                sampled);

        scopes.configureScope(
            scope -> {
              scope.withPropagationContext(
                  oldPropagationContext -> {
                    scope.setPropagationContext(propagationContext);
                  });
            });

        // TODO [POTEL] can we use OTel Sampler to let OTel know our sampling decision
        // Sentry not sampled vs OTel not sampled may mean different things for trace propagation
        samplingDecision =
            new TracesSamplingDecision(
                sampled,
                sampleRate,
                profileSampled == null ? false : profileSampled,
                profileSampleRate);
      } else {
        // span not created by Sentry API

        final @NotNull String traceId = otelSpan.getSpanContext().getTraceId();
        final @NotNull String spanId = otelSpan.getSpanContext().getSpanId();
        final @NotNull SpanId sentrySpanId = new SpanId(spanId);

        @Nullable
        SentryTraceHeader sentryTraceHeader = parentContext.get(SentryOtelKeys.SENTRY_TRACE_KEY);
        @Nullable Baggage baggageFromContext = parentContext.get(SentryOtelKeys.SENTRY_BAGGAGE_KEY);
        if (sentryTraceHeader != null) {
          baggage = baggageFromContext;
        }

        final @NotNull PropagationContext propagationContext =
            sentryTraceHeader == null
                ? new PropagationContext(new SentryId(traceId), sentrySpanId, null, baggage, null)
                : PropagationContext.fromHeaders(sentryTraceHeader, baggage, sentrySpanId);

        scopes.configureScope(
            scope -> {
              scope.withPropagationContext(
                  oldPropagationContext -> {
                    scope.setPropagationContext(propagationContext);
                  });
            });

        final @NotNull TransactionContext transactionContext =
            TransactionContext.fromPropagationContext(propagationContext);
        samplingDecision = tracesSampler.sample(new SamplingContext(transactionContext, null));
      }
    }
    final @NotNull SpanContext spanContext = otelSpan.getSpanContext();
    final @NotNull SentryDate startTimestamp =
        new SentryLongDate(otelSpan.toSpanData().getStartEpochNanos());
    spanStorage.storeSentrySpan(
        spanContext,
        new OtelSpanWrapper(
            otelSpan, scopes, startTimestamp, samplingDecision, sentryParentSpan, baggage));
  }

  @Override
  public boolean isStartRequired() {
    return true;
  }

  @Override
  public void onEnd(final @NotNull ReadableSpan spanBeingEnded) {
    final @Nullable OtelSpanWrapper sentrySpan =
        spanStorage.getSentrySpan(spanBeingEnded.getSpanContext());
    if (sentrySpan != null) {
      final @NotNull SentryDate finishDate =
          new SentryLongDate(spanBeingEnded.toSpanData().getEndEpochNanos());
      sentrySpan.updateEndDate(finishDate);
    }
    System.out.println("span ended: " + spanBeingEnded.getSpanContext().getSpanId());
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
