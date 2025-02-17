package io.sentry.opentelemetry;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.Context;
import io.sentry.Baggage;
import io.sentry.BuildConfig;
import io.sentry.IScopes;
import io.sentry.ISpan;
import io.sentry.ISpanFactory;
import io.sentry.ITransaction;
import io.sentry.NoOpSpan;
import io.sentry.NoOpTransaction;
import io.sentry.SentryDate;
import io.sentry.SpanContext;
import io.sentry.SpanId;
import io.sentry.SpanOptions;
import io.sentry.TracesSamplingDecision;
import io.sentry.TransactionContext;
import io.sentry.TransactionOptions;
import io.sentry.TransactionPerformanceCollector;
import io.sentry.protocol.SentryId;
import io.sentry.util.SpanUtils;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class OtelSpanFactory implements ISpanFactory {

  private final @NotNull SentryWeakSpanStorage storage = SentryWeakSpanStorage.getInstance();
  private final @Nullable OpenTelemetry openTelemetry;

  public OtelSpanFactory(final @Nullable OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  public OtelSpanFactory() {
    this(null);
  }

  @Override
  public @NotNull ITransaction createTransaction(
      @NotNull TransactionContext context,
      @NotNull IScopes scopes,
      @NotNull TransactionOptions transactionOptions,
      @Nullable TransactionPerformanceCollector transactionPerformanceCollector) {
    final @Nullable IOtelSpanWrapper span =
        createSpanInternal(
            scopes, transactionOptions, null, context.getSamplingDecision(), context);
    if (span == null) {
      return NoOpTransaction.getInstance();
    }
    return new OtelTransactionSpanForwarder(span);
  }

  @Override
  public @NotNull ISpan createSpan(
      final @NotNull IScopes scopes,
      final @NotNull SpanOptions spanOptions,
      final @NotNull SpanContext spanContext,
      final @Nullable ISpan parentSpan) {
    if (SpanUtils.isIgnored(scopes.getOptions().getIgnoredSpanOrigins(), spanOptions.getOrigin())) {
      return NoOpSpan.getInstance();
    }

    final @Nullable TracesSamplingDecision samplingDecision =
        parentSpan == null ? null : parentSpan.getSamplingDecision();
    final @Nullable IOtelSpanWrapper span =
        createSpanInternal(scopes, spanOptions, parentSpan, samplingDecision, spanContext);
    if (span == null) {
      return NoOpSpan.getInstance();
    }
    return span;
  }

  private @Nullable IOtelSpanWrapper createSpanInternal(
      final @NotNull IScopes scopes,
      final @NotNull SpanOptions spanOptions,
      final @Nullable ISpan parentSpan,
      final @Nullable TracesSamplingDecision samplingDecision,
      final @NotNull SpanContext spanContext) {
    final @NotNull String name = spanContext.getOperation();
    final @NotNull SpanBuilder spanBuilder = getTracer().spanBuilder(name);
    if (parentSpan == null) {
      final @NotNull SentryId traceId = spanContext.getTraceId();
      final @Nullable SpanId parentSpanId = spanContext.getParentSpanId();
      if (parentSpanId == null) {
        final @NotNull io.opentelemetry.api.trace.SpanContext otelSpanContext =
            io.opentelemetry.api.trace.SpanContext.create(
                traceId.toString(),
                io.opentelemetry.api.trace.SpanId.getInvalid(),
                TraceFlags.getSampled(),
                TraceState.getDefault());
        final @NotNull Span wrappedSpan = Span.wrap(otelSpanContext);
        spanBuilder.setParent(wrappedSpan.storeInContext(Context.current()));
      } else {
        final @NotNull io.opentelemetry.api.trace.SpanContext otelSpanContext =
            io.opentelemetry.api.trace.SpanContext.createFromRemoteParent(
                traceId.toString(),
                parentSpanId.toString(),
                TraceFlags.getSampled(),
                TraceState.getDefault());
        final @NotNull Span wrappedSpan = Span.wrap(otelSpanContext);
        spanBuilder.setParent(wrappedSpan.storeInContext(Context.current()));
      }
    } else {
      if (parentSpan instanceof IOtelSpanWrapper) {
        IOtelSpanWrapper parentSpanWrapper = (IOtelSpanWrapper) parentSpan;
        spanBuilder.setParent(parentSpanWrapper.storeInContext(Context.current()));
      }
    }

    // note: won't go through propagators
    final @Nullable Baggage baggage = spanContext.getBaggage();
    if (baggage != null) {
      spanBuilder.setAttribute(InternalSemanticAttributes.BAGGAGE_MUTABLE, baggage.isMutable());
      spanBuilder.setAttribute(InternalSemanticAttributes.BAGGAGE, baggage.toHeaderString(null));
    }

    final @Nullable SentryDate startTimestampFromOptions = spanOptions.getStartTimestamp();
    final @NotNull SentryDate startTimestamp =
        startTimestampFromOptions == null
            ? scopes.getOptions().getDateProvider().now()
            : startTimestampFromOptions;
    spanBuilder.setStartTimestamp(startTimestamp.nanoTimestamp(), TimeUnit.NANOSECONDS);

    if (samplingDecision != null) {
      spanBuilder.setAttribute(InternalSemanticAttributes.SAMPLED, samplingDecision.getSampled());
      spanBuilder.setAttribute(
          InternalSemanticAttributes.SAMPLE_RATE, samplingDecision.getSampleRate());
      spanBuilder.setAttribute(
          InternalSemanticAttributes.SAMPLE_RAND, samplingDecision.getSampleRand());
      spanBuilder.setAttribute(
          InternalSemanticAttributes.PROFILE_SAMPLED, samplingDecision.getProfileSampled());
      spanBuilder.setAttribute(
          InternalSemanticAttributes.PROFILE_SAMPLE_RATE, samplingDecision.getProfileSampleRate());
    }

    final @NotNull Span otelSpan = spanBuilder.startSpan();

    final @Nullable IOtelSpanWrapper sentrySpan = storage.getSentrySpan(otelSpan.getSpanContext());
    if (sentrySpan != null) {
      final @Nullable String description = spanContext.getDescription();
      if (description != null) {
        sentrySpan.setDescription(description);
      }
      if (spanContext instanceof TransactionContext) {
        final @NotNull TransactionContext transactionContext = (TransactionContext) spanContext;
        sentrySpan.setTransactionName(
            transactionContext.getName(), transactionContext.getTransactionNameSource());
      }
      sentrySpan.getSpanContext().setOrigin(spanOptions.getOrigin());
    }

    if (sentrySpan == null) {
      return null;
    } else {
      return new OtelStrongRefSpanWrapper(otelSpan, sentrySpan);
    }
  }

  private @NotNull Tracer getTracer() {
    return getTracerProvider().get("sentry-opentelemetry", BuildConfig.VERSION_NAME);
  }

  private @NotNull TracerProvider getTracerProvider() {
    if (openTelemetry != null) {
      return openTelemetry.getTracerProvider();
    }
    return GlobalOpenTelemetry.getTracerProvider();
  }
}
