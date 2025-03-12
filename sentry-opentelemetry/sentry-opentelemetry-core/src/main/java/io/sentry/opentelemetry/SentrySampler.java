package io.sentry.opentelemetry;

import static io.sentry.opentelemetry.OtelInternalSpanDetectionUtil.isSentryRequest;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingDecision;
import io.opentelemetry.sdk.trace.samplers.SamplingResult;
import io.sentry.Baggage;
import io.sentry.DataCategory;
import io.sentry.IScopes;
import io.sentry.PropagationContext;
import io.sentry.SamplingContext;
import io.sentry.ScopesAdapter;
import io.sentry.SentryLevel;
import io.sentry.SentryTraceHeader;
import io.sentry.SpanId;
import io.sentry.TracesSamplingDecision;
import io.sentry.TransactionContext;
import io.sentry.clientreport.DiscardReason;
import io.sentry.protocol.SentryId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SentrySampler implements Sampler {

  private final @NotNull SentryWeakSpanStorage spanStorage = SentryWeakSpanStorage.getInstance();
  private final @NotNull IScopes scopes;

  public SentrySampler(final @NotNull IScopes scopes) {
    this.scopes = scopes;
  }

  public SentrySampler() {
    this(ScopesAdapter.getInstance());
  }

  @Override
  public SamplingResult shouldSample(
      final @NotNull Context parentContext,
      final @NotNull String traceId,
      final @NotNull String name,
      final @NotNull SpanKind spanKind,
      final @NotNull Attributes attributes,
      final @NotNull List<LinkData> parentLinks) {
    if (isSentryRequest(scopes, spanKind, attributes)) {
      return SamplingResult.drop();
    }
    // note: parentLinks seems to usually be empty
    final @Nullable Span parentOtelSpan = Span.fromContextOrNull(parentContext);
    final @Nullable IOtelSpanWrapper parentSentrySpan =
        parentOtelSpan != null ? spanStorage.getSentrySpan(parentOtelSpan.getSpanContext()) : null;

    if (parentSentrySpan != null) {
      return copyParentSentryDecision(parentSentrySpan);
    } else {
      final @Nullable TracesSamplingDecision samplingDecision =
          OtelSamplingUtil.extractSamplingDecision(attributes);
      if (samplingDecision != null) {
        return new SentrySamplingResult(samplingDecision);
      } else {
        return handleRootOtelSpan(traceId, parentContext, attributes);
      }
    }
  }

  private @NotNull SamplingResult handleRootOtelSpan(
      final @NotNull String traceId, final @NotNull Context parentContext, final @NotNull Attributes attributes) {
    if (!scopes.getOptions().isTracingEnabled()) {
      return SamplingResult.create(SamplingDecision.RECORD_ONLY);
    }
    @Nullable Baggage baggage = null;
    @Nullable
    SentryTraceHeader sentryTraceHeader = parentContext.get(SentryOtelKeys.SENTRY_TRACE_KEY);
    @Nullable Baggage baggageFromContext = parentContext.get(SentryOtelKeys.SENTRY_BAGGAGE_KEY);
    if (sentryTraceHeader != null) {
      baggage = baggageFromContext;
    }

    // there's no way to get the span id here, so we just use a random id for sampling
    SpanId randomSpanId = new SpanId();
    final @NotNull PropagationContext propagationContext =
        sentryTraceHeader == null
            ? new PropagationContext(new SentryId(traceId), randomSpanId, null, baggage, null)
            : PropagationContext.fromHeaders(sentryTraceHeader, baggage, randomSpanId);

    final @NotNull TransactionContext transactionContext =
        TransactionContext.fromPropagationContext(propagationContext);
    final @NotNull TracesSamplingDecision sentryDecision =
        scopes
            .getOptions()
            .getInternalTracesSampler()
            .sample(
                new SamplingContext(transactionContext, null, propagationContext.getSampleRand(), toMapWithStringKeys(attributes)));

    if (!sentryDecision.getSampled()) {
      scopes
          .getOptions()
          .getClientReportRecorder()
          .recordLostEvent(DiscardReason.SAMPLE_RATE, DataCategory.Transaction);
      scopes
          .getOptions()
          .getClientReportRecorder()
          .recordLostEvent(DiscardReason.SAMPLE_RATE, DataCategory.Span);
    }

    return new SentrySamplingResult(sentryDecision);
  }

  private @NotNull SentrySamplingResult copyParentSentryDecision(
      final @NotNull IOtelSpanWrapper parentSentrySpan) {
    final @Nullable TracesSamplingDecision parentSamplingDecision =
        parentSentrySpan.getSamplingDecision();
    if (parentSamplingDecision != null) {
      if (!parentSamplingDecision.getSampled()) {
        scopes
            .getOptions()
            .getClientReportRecorder()
            .recordLostEvent(DiscardReason.SAMPLE_RATE, DataCategory.Span);
      }
      return new SentrySamplingResult(parentSamplingDecision);
    } else {
      scopes
          .getOptions()
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Encountered a missing parent sampling decision where one was expected.");
      return new SentrySamplingResult(new TracesSamplingDecision(true));
    }
  }

  private @NotNull Map<String, Object> toMapWithStringKeys(final @NotNull Attributes attributes) {
    final @NotNull Map<String, Object> mapWithStringKeys = new HashMap<>(attributes.size());

    if (attributes != null) {
      attributes.forEach(
        (key, value) -> {
          if (key != null) {
            final @NotNull String stringKey = key.getKey();
              mapWithStringKeys.put(stringKey, value);
            }
        });
    }

    return mapWithStringKeys;
  }

  @Override
  public String getDescription() {
    return "SentrySampler";
  }
}
