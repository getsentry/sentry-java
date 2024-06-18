package io.sentry.opentelemetry;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.sentry.BaggageHeader;
import io.sentry.IScopes;
import io.sentry.ISentryLifecycleToken;
import io.sentry.ISpan;
import io.sentry.Instrumenter;
import io.sentry.MeasurementUnit;
import io.sentry.NoOpScopesStorage;
import io.sentry.NoOpSpan;
import io.sentry.SentryDate;
import io.sentry.SentryLevel;
import io.sentry.SentryTraceHeader;
import io.sentry.SpanContext;
import io.sentry.SpanId;
import io.sentry.SpanOptions;
import io.sentry.SpanStatus;
import io.sentry.TraceContext;
import io.sentry.TracesSamplingDecision;
import io.sentry.metrics.LocalMetricsAggregator;
import io.sentry.protocol.Contexts;
import io.sentry.protocol.MeasurementValue;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.TransactionNameSource;
import io.sentry.util.LazyEvaluator;
import io.sentry.util.Objects;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** NOTE: This wrapper is not used when using OpenTelemetry API, only when using Sentry API. */
@ApiStatus.Internal
public final class OtelSpanWrapper implements ISpan {

  private final @NotNull IScopes scopes;

  /** The moment in time when span was started. */
  private @NotNull SentryDate startTimestamp;

  private @Nullable SentryDate finishedTimestamp = null;

  /**
   * OpenTelemetry span which this wrapper wraps. Needs to be referenced weakly as otherwise we'd
   * create a circular reference from {@link io.opentelemetry.sdk.trace.data.SpanData} to {@link
   * OtelSpanWrapper} and indirectly back to {@link io.opentelemetry.sdk.trace.data.SpanData} via
   * {@link Span}. Also see {@link SentryWeakSpanStorage}.
   */
  private final @NotNull WeakReference<ReadWriteSpan> span;

  private final @NotNull SpanContext context;
  //  private final @NotNull SpanOptions options;
  private final @NotNull Contexts contexts = new Contexts();
  private @Nullable String transactionName;
  private @Nullable TransactionNameSource transactionNameSource;

  // TODO [POTEL]
  //  private @Nullable SpanFinishedCallback spanFinishedCallback;

  private final @NotNull Map<String, Object> data = new ConcurrentHashMap<>();
  private final @NotNull Map<String, MeasurementValue> measurements = new ConcurrentHashMap<>();

  @SuppressWarnings("Convert2MethodRef") // older AGP versions do not support method references
  private final @NotNull LazyEvaluator<LocalMetricsAggregator> metricsAggregator =
      new LazyEvaluator<>(() -> new LocalMetricsAggregator());

  /** A throwable thrown during the execution of the span. */
  private @Nullable Throwable throwable;

  private @NotNull Deque<ISentryLifecycleToken> tokensToCleanup = new ArrayDeque<>(1);

  public OtelSpanWrapper(
      final @NotNull ReadWriteSpan span,
      final @NotNull IScopes scopes,
      final @NotNull SentryDate startTimestamp,
      final @Nullable TracesSamplingDecision samplingDecision,
      final @Nullable OtelSpanWrapper parentSpan) {
    this.scopes = Objects.requireNonNull(scopes, "scopes are required");
    this.span = new WeakReference<>(span);
    this.startTimestamp = startTimestamp;
    this.context = new OtelSpanContext(span, samplingDecision, parentSpan);
  }

  @Override
  public @NotNull ISpan startChild(@NotNull String operation) {
    return startChild(operation, (String) null);
  }

  @Override
  public @NotNull ISpan startChild(
      @NotNull String operation, @Nullable String description, @NotNull SpanOptions spanOptions) {
    if (isFinished()) {
      return NoOpSpan.getInstance();
    }
    final @NotNull SpanContext spanContext =
        context.copyForChild(operation, getSpanContext().getSpanId(), null);
    spanContext.setDescription(description);

    return startChild(spanContext, spanOptions);
  }

  @Override
  public @NotNull ISpan startChild(
      @NotNull SpanContext spanContext, @NotNull SpanOptions spanOptions) {
    if (isFinished()) {
      return NoOpSpan.getInstance();
    }

    return scopes.getOptions().getSpanFactory().createSpan(scopes, spanOptions, spanContext, this);
  }

  @Override
  public @NotNull ISpan startChild(
      @NotNull String operation,
      @Nullable String description,
      @Nullable SentryDate timestamp,
      @NotNull Instrumenter instrumenter) {
    final @NotNull SpanContext spanContext =
        context.copyForChild(operation, getSpanContext().getSpanId(), null);
    spanContext.setDescription(description);
    spanContext.setInstrumenter(instrumenter);

    final @NotNull SpanOptions spanOptions = new SpanOptions();
    spanOptions.setStartTimestamp(timestamp);

    return startChild(spanContext, spanOptions);
  }

  @Override
  public @NotNull ISpan startChild(
      @NotNull String operation,
      @Nullable String description,
      @Nullable SentryDate timestamp,
      @NotNull Instrumenter instrumenter,
      @NotNull SpanOptions spanOptions) {
    if (timestamp != null) {
      spanOptions.setStartTimestamp(timestamp);
    }

    final @NotNull SpanContext spanContext =
        context.copyForChild(operation, getSpanContext().getSpanId(), null);
    spanContext.setDescription(description);
    spanContext.setInstrumenter(instrumenter);

    return startChild(spanContext, spanOptions);
  }

  @Override
  public @NotNull ISpan startChild(@NotNull String operation, @Nullable String description) {
    final @NotNull SpanContext spanContext =
        context.copyForChild(operation, getSpanContext().getSpanId(), null);
    spanContext.setDescription(description);

    return startChild(spanContext, new SpanOptions());
  }

  @Override
  public @NotNull SentryTraceHeader toSentryTrace() {
    return new SentryTraceHeader(getTraceId(), getOtelSpanId(), isSampled());
  }

  private @NotNull SpanId getOtelSpanId() {
    return context.getSpanId();
  }

  private @Nullable ReadWriteSpan getSpan() {
    return span.get();
  }

  @Override
  public @Nullable TraceContext traceContext() {
    //    return transaction.traceContext();
    // TODO [POTEL]
    return null;
  }

  @Override
  public @Nullable BaggageHeader toBaggageHeader(@Nullable List<String> thirdPartyBaggageHeaders) {
    //    return transaction.toBaggageHeader(thirdPartyBaggageHeaders);
    // TODO [POTEL]
    return null;
  }

  @Override
  public void finish() {
    finish(getStatus());
  }

  @Override
  public void finish(@Nullable SpanStatus status) {
    setStatus(status);
    final @Nullable Span otelSpan = getSpan();
    if (otelSpan != null) {
      otelSpan.end();
    }

    for (ISentryLifecycleToken token : tokensToCleanup) {
      token.close();
    }
  }

  @Override
  public void finish(@Nullable SpanStatus status, @Nullable SentryDate timestamp) {
    setStatus(status);
    final @Nullable Span otelSpan = getSpan();
    if (otelSpan != null) {
      if (timestamp != null) {
        otelSpan.end(timestamp.nanoTimestamp(), TimeUnit.NANOSECONDS);
      } else {
        otelSpan.end();
      }
    }
  }

  @Override
  public void setOperation(@NotNull String operation) {
    this.context.setOperation(operation);
  }

  @Override
  public @NotNull String getOperation() {
    return context.getOperation();
  }

  @Override
  public void setDescription(@Nullable String description) {
    this.context.setDescription(description);
  }

  @Override
  public @Nullable String getDescription() {
    return this.context.getDescription();
  }

  @Override
  public void setStatus(final @Nullable SpanStatus status) {
    context.setStatus(status);
  }

  @Override
  public @Nullable SpanStatus getStatus() {
    return context.getStatus();
  }

  @Override
  public void setThrowable(@Nullable Throwable throwable) {
    this.throwable = throwable;
  }

  @Override
  public @Nullable Throwable getThrowable() {
    return throwable;
  }

  @Override
  public @NotNull SpanContext getSpanContext() {
    return context;
  }

  @Override
  public void setTag(@NotNull String key, @NotNull String value) {
    context.setTag(key, value);
  }

  @Override
  public @Nullable String getTag(@NotNull String key) {
    return context.getTags().get(key);
  }

  @ApiStatus.Internal
  public @NotNull Map<String, String> getTags() {
    return context.getTags();
  }

  @Override
  public boolean isFinished() {
    final @Nullable ReadWriteSpan otelSpan = getSpan();
    if (otelSpan != null) {
      return otelSpan.hasEnded();
    }

    // if span is no longer available we consider it ended/finished
    return true;
  }

  @Override
  public void setData(@NotNull String key, @NotNull Object value) {
    data.put(key, value);
  }

  @Override
  public @Nullable Object getData(@NotNull String key) {
    return data.get(key);
  }

  @Override
  public void setMeasurement(@NotNull String name, @NotNull Number value) {
    if (isFinished()) {
      scopes
          .getOptions()
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "The span is already finished. Measurement %s cannot be set",
              name);
      return;
    }
    this.measurements.put(name, new MeasurementValue(value, null));

    // TODO [POTEL] can't set on transaction
    // We set the measurement in the transaction, too, but we have to check if this is the root span
    // of the transaction, to avoid an infinite recursion
    //    if (transaction.getRoot() != this) {
    //      transaction.setMeasurementFromChild(name, value);
    //    }
  }

  @Override
  public void setMeasurement(
      @NotNull String name, @NotNull Number value, @NotNull MeasurementUnit unit) {
    if (isFinished()) {
      scopes
          .getOptions()
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "The span is already finished. Measurement %s cannot be set",
              name);
      return;
    }
    this.measurements.put(name, new MeasurementValue(value, unit.apiName()));

    // TODO [POTEL] can't set on transaction
    // We set the measurement in the transaction, too, but we have to check if this is the root span
    // of the transaction, to avoid an infinite recursion
    //    if (transaction.getRoot() != this) {
    //      transaction.setMeasurementFromChild(name, value, unit);
    //    }
  }

  @Override
  public boolean updateEndDate(@NotNull SentryDate date) {
    if (this.finishedTimestamp != null) {
      this.finishedTimestamp = date;
      return true;
    }
    return false;
  }

  @Override
  public @NotNull SentryDate getStartDate() {
    return startTimestamp;
  }

  @Override
  public @Nullable SentryDate getFinishDate() {
    return finishedTimestamp;
  }

  @Override
  public boolean isNoOp() {
    return false;
  }

  @Override
  public @Nullable LocalMetricsAggregator getLocalMetricsAggregator() {
    return metricsAggregator.getValue();
  }

  @Override
  public void setContext(@NotNull String key, @NotNull Object context) {
    contexts.put(key, context);
  }

  @Override
  public @NotNull Contexts getContexts() {
    // TODO [POTEL] only works for root span atm
    return contexts;
  }

  public void setTransactionName(@NotNull String name) {
    setTransactionName(name, TransactionNameSource.CUSTOM);
  }

  public void setTransactionName(@NotNull String name, @NotNull TransactionNameSource nameSource) {
    this.transactionName = name;
    this.transactionNameSource = nameSource;
  }

  @ApiStatus.Internal
  public @Nullable TransactionNameSource getTransactionNameSource() {
    return transactionNameSource;
  }

  @ApiStatus.Internal
  public @Nullable String getTransactionName() {
    return this.transactionName;
  }

  @NotNull
  public SentryId getTraceId() {
    return context.getTraceId();
  }

  public @NotNull Map<String, Object> getData() {
    return data;
  }

  @NotNull
  public Map<String, MeasurementValue> getMeasurements() {
    return measurements;
  }

  @Override
  public @Nullable Boolean isSampled() {
    return context.getSampled();
  }

  public @Nullable Boolean isProfileSampled() {
    return context.getProfileSampled();
  }

  @Override
  public @Nullable TracesSamplingDecision getSamplingDecision() {
    return context.getSamplingDecision();
  }

  @Override
  public @NotNull SentryId getEventId() {
    // TODO [POTEL]
    return new SentryId(getOtelSpanId().toString());
  }

  @ApiStatus.Internal
  public @NotNull IScopes getScopes() {
    return scopes;
  }

  @SuppressWarnings("MustBeClosedChecker")
  @ApiStatus.Internal
  @Override
  public @NotNull ISentryLifecycleToken makeCurrent() {
    final @Nullable Span otelSpan = getSpan();
    if (otelSpan != null) {
      final @NotNull Scope otelScope = otelSpan.makeCurrent();
      // TODO [POTEL] should we keep an ordered list of otel scopes and close them in reverse order
      // on finish?
      // TODO [POTEL] should we make transaction/span implement ISentryLifecycleToken instead?
      final @NotNull OtelContextSpanStorageToken token = new OtelContextSpanStorageToken(otelScope);
      // to iterate LIFO when closing
      tokensToCleanup.addFirst(token);
      return token;
    }
    return NoOpScopesStorage.NoOpScopesLifecycleToken.getInstance();
  }

  // TODO [POTEL] extract generic
  static final class OtelContextSpanStorageToken implements ISentryLifecycleToken {

    private final @NotNull Scope otelScope;

    OtelContextSpanStorageToken(final @NotNull Scope otelScope) {
      this.otelScope = otelScope;
    }

    @Override
    public void close() {
      otelScope.close();
    }
  }
}
