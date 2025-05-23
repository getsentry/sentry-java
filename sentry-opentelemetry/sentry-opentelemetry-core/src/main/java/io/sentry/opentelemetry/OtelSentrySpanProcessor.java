package io.sentry.opentelemetry;

import static io.sentry.opentelemetry.InternalSemanticAttributes.IS_REMOTE_PARENT;
import static io.sentry.opentelemetry.SentryOtelKeys.SENTRY_SCOPES_KEY;

import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.ExceptionEventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.sentry.Baggage;
import io.sentry.DateUtils;
import io.sentry.IScopes;
import io.sentry.PropagationContext;
import io.sentry.ScopesAdapter;
import io.sentry.Sentry;
import io.sentry.SentryDate;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.SentryLongDate;
import io.sentry.SentryTraceHeader;
import io.sentry.SpanId;
import io.sentry.TracesSamplingDecision;
import io.sentry.exception.ExceptionMechanismException;
import io.sentry.protocol.Mechanism;
import io.sentry.protocol.SentryId;
import java.util.List;
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

    final @NotNull IScopes scopes = forkScopes(parentContext, otelSpan.toSpanData());

    final @Nullable IOtelSpanWrapper sentryParentSpan =
        spanStorage.getSentrySpan(otelSpan.getParentSpanContext());
    @Nullable
    TracesSamplingDecision samplingDecision =
        OtelSamplingUtil.extractSamplingDecision(otelSpan.toSpanData().getAttributes());
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

      final @Nullable String baggageString =
          otelSpan.getAttribute(InternalSemanticAttributes.BAGGAGE);
      if (baggageString != null) {
        baggage = Baggage.fromHeader(baggageString);
      }

      final @Nullable Boolean sampled = isSampled(otelSpan, samplingDecision);

      final @NotNull PropagationContext propagationContext =
          new PropagationContext(
              new SentryId(traceId), sentrySpanId, sentryParentSpanId, baggage, sampled);

      baggage = propagationContext.getBaggage();
      baggage.setValuesFromSamplingDecision(samplingDecision);

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

  private IScopes forkScopes(final @NotNull Context context, final @NotNull SpanData span) {
    final @Nullable IScopes scopesFromContext = context.get(SENTRY_SCOPES_KEY);
    if (scopesFromContext == null) {
      return Sentry.forkedRootScopes("spanprocessor.new");
    }
    if (isRootSpan(span)) {
      return scopesFromContext.forkedScopes("spanprocessor.rootspan");
    }

    return scopesFromContext.forkedCurrentScope("spanprocessor.nonrootspan");
  }

  private boolean isRootSpan(SpanData otelSpan) {
    return !otelSpan.getParentSpanContext().isValid() || otelSpan.getParentSpanContext().isRemote();
  }

  private @Nullable Boolean isSampled(
      final @NotNull ReadWriteSpan otelSpan,
      final @Nullable TracesSamplingDecision samplingDecision) {
    if (samplingDecision != null) {
      return samplingDecision.getSampled();
    }

    if (otelSpan.getSpanContext().isSampled()) {
      return true;
    }

    // tracing without performance
    return null;
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

      maybeCaptureSpanEventsAsExceptions(spanBeingEnded, sentrySpan);
    }
  }

  private void maybeCaptureSpanEventsAsExceptions(
      final @NotNull ReadableSpan spanBeingEnded, final @NotNull IOtelSpanWrapper sentrySpan) {
    final @NotNull IScopes spanScopes = sentrySpan.getScopes();
    if (spanScopes.getOptions().isCaptureOpenTelemetryEvents()) {
      final @NotNull List<EventData> events = spanBeingEnded.toSpanData().getEvents();
      for (EventData event : events) {
        if (event instanceof ExceptionEventData) {
          final @NotNull ExceptionEventData exceptionEvent = (ExceptionEventData) event;
          captureException(spanScopes, exceptionEvent, sentrySpan);
        }
      }
    }
  }

  private void captureException(
      final @NotNull IScopes scopes,
      final @NotNull ExceptionEventData exceptionEvent,
      final @NotNull IOtelSpanWrapper sentrySpan) {
    final @NotNull Throwable exception = exceptionEvent.getException();
    final Mechanism mechanism = new Mechanism();
    mechanism.setType("OpenTelemetrySpanEvent");
    mechanism.setHandled(true);
    // This is potentially the wrong Thread as it's the current thread meaning the thread where
    // the span is being ended on. This may not match the thread where the exception occurred.
    final Throwable mechanismException =
        new ExceptionMechanismException(mechanism, exception, Thread.currentThread());

    final SentryEvent event = new SentryEvent(mechanismException);
    event.setTimestamp(DateUtils.nanosToDate(exceptionEvent.getEpochNanos()));
    event.setLevel(SentryLevel.ERROR);
    event.getContexts().setTrace(sentrySpan.getSpanContext());

    scopes.captureEvent(event);
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
