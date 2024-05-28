package io.sentry.opentelemetry;

import static io.sentry.opentelemetry.InternalSemanticAttributes.IS_REMOTE_PARENT;
import static io.sentry.opentelemetry.SentryOtelKeys.SENTRY_SCOPES_KEY;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.sentry.IScopes;
import io.sentry.PropagationContext;
import io.sentry.SamplingContext;
import io.sentry.ScopesAdapter;
import io.sentry.Sentry;
import io.sentry.SentryDate;
import io.sentry.SentryLevel;
import io.sentry.SentryLongDate;
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

    final @Nullable Span parentSpan = Span.fromContextOrNull(parentContext);
    final @Nullable OtelSpanWrapper sentryParentSpan =
        spanStorage.getSentrySpan(otelSpan.getParentSpanContext());
    @Nullable TracesSamplingDecision samplingDecision = null;
    if (sentryParentSpan == null) {
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
                new SentryId(traceId), new SpanId(spanId), new SpanId(parentSpanId), null, sampled);

        scopes.configureScope(
            scope -> {
              scope.withPropagationContext(
                  oldPropagationContext -> {
                    scope.setPropagationContext(propagationContext);
                  });
            });

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

        final @NotNull PropagationContext propagationContext =
            new PropagationContext(new SentryId(traceId), new SpanId(spanId), null, null, null);

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
    } else {
      otelSpan.setAttribute(IS_REMOTE_PARENT, parentSpan.getSpanContext().isRemote());
    }
    final @NotNull SpanContext spanContext = otelSpan.getSpanContext();
    final @NotNull SentryDate startTimestamp =
        new SentryLongDate(otelSpan.toSpanData().getStartEpochNanos());
    spanStorage.storeSentrySpan(
        spanContext,
        new OtelSpanWrapper(otelSpan, scopes, startTimestamp, samplingDecision, sentryParentSpan));
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
