package io.sentry;

import io.sentry.exception.InvalidSentryTraceHeaderException;
import io.sentry.protocol.SentryId;
import io.sentry.util.SampleRateUtils;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class PropagationContext {

  public static PropagationContext fromHeaders(
      final @NotNull ILogger logger,
      final @Nullable String sentryTraceHeader,
      final @Nullable String baggageHeader) {
    return fromHeaders(logger, sentryTraceHeader, Arrays.asList(baggageHeader));
  }

  public static @NotNull PropagationContext fromHeaders(
      final @NotNull ILogger logger,
      final @Nullable String sentryTraceHeaderString,
      final @Nullable List<String> baggageHeaderStrings) {
    if (sentryTraceHeaderString == null) {
      return new PropagationContext();
    }

    try {
      final @NotNull SentryTraceHeader traceHeader = new SentryTraceHeader(sentryTraceHeaderString);
      final @NotNull Baggage baggage = Baggage.fromHeader(baggageHeaderStrings, logger);
      return fromHeaders(traceHeader, baggage, null);
    } catch (InvalidSentryTraceHeaderException e) {
      logger.log(SentryLevel.DEBUG, e, "Failed to parse Sentry trace header: %s", e.getMessage());
      return new PropagationContext();
    }
  }

  public static @NotNull PropagationContext fromHeaders(
      final @NotNull SentryTraceHeader sentryTraceHeader,
      final @Nullable Baggage baggage,
      final @Nullable SpanId spanId) {
    final @NotNull SpanId spanIdToUse = spanId == null ? new SpanId() : spanId;

    return new PropagationContext(
        sentryTraceHeader.getTraceId(),
        spanIdToUse,
        sentryTraceHeader.getSpanId(),
        baggage,
        sentryTraceHeader.isSampled(),
        null);
  }

  private @NotNull SentryId traceId;
  private @NotNull SpanId spanId;
  private @Nullable SpanId parentSpanId;

  private @Nullable Boolean sampled;
  private @NotNull Double sampleRand;

  private @NotNull Baggage baggage;

  public PropagationContext() {
    this(new SentryId(), new SpanId(), null, null, null, null);
  }

  public PropagationContext(final @NotNull PropagationContext propagationContext) {
    this(
        propagationContext.getTraceId(),
        propagationContext.getSpanId(),
        propagationContext.getParentSpanId(),
        propagationContext.getBaggage(),
        propagationContext.isSampled(),
        propagationContext.getSampleRand());
  }

  @SuppressWarnings("UnusedMethod")
  private static @Nullable Baggage cloneBaggage(final @Nullable Baggage baggage) {
    if (baggage != null) {
      return new Baggage(baggage);
    }

    return null;
  }

  @SuppressWarnings("ObjectToString")
  public PropagationContext(
      final @NotNull SentryId traceId,
      final @NotNull SpanId spanId,
      final @Nullable SpanId parentSpanId,
      final @Nullable Baggage baggage,
      final @Nullable Boolean sampled,
      final @Nullable Double sampleRand) {
    this.traceId = traceId;
    this.spanId = spanId;
    this.parentSpanId = parentSpanId;
    boolean shouldFreezeBaggage = false;
    if (baggage != null) {
      this.baggage = baggage;
      shouldFreezeBaggage = true;
    } else {
      this.baggage = new Baggage(ScopesAdapter.getInstance().getOptions().getLogger());
    }
    this.sampled = sampled;
    StringBuilder sb = new StringBuilder("sample rand");
    if (sampleRand != null) {
      sb.append(" [passed in as param]");
      this.sampleRand = sampleRand;
    } else {
      sb.append(" [maybe baggage maybe backfill]");
      final @Nullable Double sampleRandMaybe = this.baggage.getSampleRandDouble();
      sb.append(" [baggage " + sampleRandMaybe + "]");
      final @Nullable Double sampleRateMaybe = this.baggage.getSampleRateDouble();
      this.sampleRand =
          SampleRateUtils.backfilledSampleRand(sampleRandMaybe, sampleRateMaybe, sampled);
    }
    if (this.baggage.getSampleRand() == null) {
      sb.append(" [setting sample rand on baggage " + this.baggage + "]");
      this.baggage.setSampleRandDouble(this.sampleRand);
    }
    if (shouldFreezeBaggage) {
      if (this.baggage.isMutable()) {
        sb.append(" [freezing baggage]");
        this.baggage.freeze();
      } else {
        sb.append(" [baggage already frozen]");
      }
    }
    sb.append(" {" + this.sampleRand + "}");
    new RuntimeException("PropagationContext ctor" + sb.toString()).printStackTrace();
  }

  public @NotNull SentryId getTraceId() {
    return traceId;
  }

  public void setTraceId(final @NotNull SentryId traceId) {
    this.traceId = traceId;
  }

  public @NotNull SpanId getSpanId() {
    return spanId;
  }

  public void setSpanId(final @NotNull SpanId spanId) {
    this.spanId = spanId;
  }

  public @Nullable SpanId getParentSpanId() {
    return parentSpanId;
  }

  public void setParentSpanId(final @Nullable SpanId parentSpanId) {
    this.parentSpanId = parentSpanId;
  }

  public @NotNull Baggage getBaggage() {
    return baggage;
  }

  public @Nullable Boolean isSampled() {
    return sampled;
  }

  public void setSampled(final @Nullable Boolean sampled) {
    this.sampled = sampled;
  }

  public @Nullable TraceContext traceContext() {
    if (baggage != null) {
      return baggage.toTraceContext();
    }

    return null;
  }

  public @NotNull SpanContext toSpanContext() {
    final SpanContext spanContext = new SpanContext(traceId, spanId, "default", null, null);
    spanContext.setOrigin("auto");
    return spanContext;
  }

  public @NotNull Double getSampleRand() {
    return sampleRand;
  }
}
