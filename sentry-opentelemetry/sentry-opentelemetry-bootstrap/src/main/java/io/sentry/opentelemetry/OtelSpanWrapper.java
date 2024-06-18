package io.sentry.opentelemetry;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.sentry.BaggageHeader;
import io.sentry.IScopes;
import io.sentry.ISentryLifecycleToken;
import io.sentry.ISpan;
import io.sentry.Instrumenter;
import io.sentry.MeasurementUnit;
import io.sentry.NoOpScopesStorage;
import io.sentry.SentryDate;
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
import io.sentry.util.Objects;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class OtelSpanWrapper implements ISpan {

  private final @NotNull IScopes scopes;

  /** The moment in time when span was started. */
  private @NotNull SentryDate startTimestamp;
  // TODO [POTEL] Set end timestamp in SpanProcessor, read it in exporter
  //  private @Nullable SentryDate endTimestamp = null;

  /**
   * OpenTelemetry span which this wrapper wraps. Needs to be referenced weakly as otherwise we'd
   * create a circular reference from {@link io.opentelemetry.sdk.trace.data.SpanData} to {@link
   * OtelSpanWrapper} and indirectly back to {@link io.opentelemetry.sdk.trace.data.SpanData} via
   * {@link Span}. Also see {@link SentryWeakSpanStorage}.
   */
  private final @NotNull WeakReference<Span> span;
  //  private final @NotNull SpanContext context;
  //  private final @NotNull SpanOptions options;
  private final @NotNull Contexts contexts = new Contexts();
  // TODO [POTEL] should be on SpanContext and retrieved from there in ctor here
  private @NotNull TransactionNameSource nameSource = TransactionNameSource.CUSTOM;
  private @NotNull String name = "<unlabeled span>";

  //  public OtelSpanWrapper(
  //      final @NotNull SpanBuilder spanBuilder,
  //      final @NotNull TransactionContext context,
  //      final @NotNull IScopes scopes,
  //      final @Nullable SentryDate startTimestamp,
  //      final @NotNull SpanOptions options) {
  ////    this.context = Objects.requireNonNull(context, "context is required");
  ////    this.transaction = Objects.requireNonNull(transaction, "transaction is required");
  //    this.scopes = Objects.requireNonNull(scopes, "scopes are required");
  //    //    this.spanFinishedCallback = null;
  //    if (startTimestamp != null) {
  //      this.startTimestamp = startTimestamp;
  //    } else {
  //      this.startTimestamp = scopes.getOptions().getDateProvider().now();
  //    }
  //    spanBuilder.setStartTimestamp(this.startTimestamp.nanoTimestamp(), TimeUnit.NANOSECONDS);
  //    spanBuilder.setNoParent();
  //    //    this.options = options;
  //    this.span = new WeakReference<>(spanBuilder.startSpan());
  //  }

  public OtelSpanWrapper(final @NotNull Span span, final @NotNull IScopes scopes) {
    this.scopes = Objects.requireNonNull(scopes, "scopes are required");
    this.span = new WeakReference<>(span);
    // TODO [POTEL] how could we make this work?
    this.startTimestamp = scopes.getOptions().getDateProvider().now();
  }

  //  OtelSpanWrapper(
  //      final @NotNull SpanBuilder spanBuilder,
  //      final @NotNull SentryId traceId,
  //      final @Nullable SpanId parentSpanId,
  //      final @NotNull String operation,
  //      final @NotNull IScopes scopes,
  //      final @Nullable SentryDate startTimestamp,
  //      final @NotNull SpanOptions options
  //      /*final @Nullable SpanFinishedCallback spanFinishedCallback*/ ) {
  //    this.scopes = Objects.requireNonNull(scopes, "scopes are required");
  ////    this.context =
  ////        new SpanContext(
  ////            traceId, new SpanId(), operation, parentSpanId,
  // transaction.getSamplingDecision());
  ////    this.transaction = Objects.requireNonNull(transaction, "transaction is required");
  //    Objects.requireNonNull(scopes, "Scopes are required");
  //    //    this.options = options;
  //    //    this.spanFinishedCallback = spanFinishedCallback;
  //    if (startTimestamp != null) {
  //      this.startTimestamp = startTimestamp;
  //    } else {
  //      this.startTimestamp = scopes.getOptions().getDateProvider().now();
  //    }
  //    spanBuilder.setStartTimestamp(this.startTimestamp.nanoTimestamp(), TimeUnit.NANOSECONDS);
  //    this.span = new WeakReference<>(spanBuilder.startSpan());
  //  }

  @Override
  public @NotNull ISpan startChild(@NotNull String operation) {
    return startChild(operation, (String) null);
  }

  @Override
  public @NotNull ISpan startChild(
      @NotNull String operation, @Nullable String description, @NotNull SpanOptions spanOptions) {
    // TODO [POTEL] check finished
    //    return transaction.startChild(context.getSpanId(), operation, description, spanOptions);
    // TODO [POTEL] use description
    return scopes.getOptions().getSpanFactory().createSpan(operation, scopes, spanOptions, this);
  }

  @Override
  public @NotNull ISpan startChild(
      @NotNull String operation,
      @Nullable String description,
      @Nullable SentryDate timestamp,
      @NotNull Instrumenter instrumenter) {
    return startChild(operation, description, timestamp, instrumenter, new SpanOptions());
  }

  @Override
  public @NotNull ISpan startChild(
      @NotNull String operation,
      @Nullable String description,
      @Nullable SentryDate timestamp,
      @NotNull Instrumenter instrumenter,
      @NotNull SpanOptions spanOptions) {
    // TODO [POTEL] check finished
    //    return transaction.startChild(
    //        context.getSpanId(), operation, description, timestamp, instrumenter, spanOptions);
    // TODO [POTEL] use description, timestamp, instrumenter
    return scopes.getOptions().getSpanFactory().createSpan(operation, scopes, spanOptions, this);
  }

  @Override
  public @NotNull ISpan startChild(@NotNull String operation, @Nullable String description) {
    // TODO [POTEL] check finished
    //    return transaction.startChild(context.getSpanId(), operation, description);
    return scopes
        .getOptions()
        .getSpanFactory()
        .createSpan(operation, scopes, new SpanOptions(), this);
  }

  @Override
  public @NotNull SentryTraceHeader toSentryTrace() {
    return new SentryTraceHeader(getTraceId(), getOtelSpanId(), isSampled());
  }

  private @NotNull SpanId getOtelSpanId() {
    final @Nullable Span otelSpan = getSpan();
    if (otelSpan != null) {
      return new SpanId(otelSpan.getSpanContext().getSpanId());
    } else {
      return SpanId.EMPTY_ID;
    }
  }

  private @Nullable Span getSpan() {
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
    //    finish(this.context.getStatus());
    // TODO [POTEL]
    finish(SpanStatus.OK);
  }

  @Override
  public void finish(@Nullable SpanStatus status) {
    setStatus(status);
    final @Nullable Span otelSpan = getSpan();
    if (otelSpan != null) {
      otelSpan.end();
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
  public void setOperation(@NotNull String operation) {}

  @Override
  public @NotNull String getOperation() {
    // TODO [POTEL]
    return "";
  }

  @Override
  public void setDescription(@Nullable String description) {
    // TODO [POTEL] need to find a way to transfer data from this wrapper to SpanExporter
    // ^ could go in span attributes
  }

  @Override
  public @Nullable String getDescription() {
    // TODO [POTEL] need to find a way to transfer data from this wrapper to SpanExporter
    return null;
  }

  @Override
  public void setStatus(@Nullable SpanStatus status) {
    // TODO [POTEL] need to find a way to transfer data from this wrapper to SpanExporter
    // ^ could go in span attributes
    //    this.context.setStatus(status);
  }

  @Override
  public @Nullable SpanStatus getStatus() {
    // TODO [POTEL] need to find a way to transfer data from this wrapper to SpanExporter
    //    return context.getStatus();
    return null;
  }

  @Override
  public void setThrowable(@Nullable Throwable throwable) {
    // TODO [POTEL] need to find a way to transfer data from this wrapper to SpanExporter
  }

  @Override
  public @Nullable Throwable getThrowable() {
    // TODO [POTEL] need to find a way to transfer data from this wrapper to SpanExporter
    return null;
  }

  @Override
  public @NotNull SpanContext getSpanContext() {
    // TODO [POTEL] usage outside: setSampled, setOrigin, getTraceId, contexts.setTrace(), status,
    // getOrigin
    // TODO [POTEL] op, util for spanid, parentSpanId
    return new SpanContext(getTraceId(), getOtelSpanId(), "TODO op", null, getSamplingDecision());
  }

  @Override
  public void setTag(@NotNull String key, @NotNull String value) {
    // TODO [POTEL] need to find a way to transfer data from this wrapper to SpanExporter
    //    context.setTag(key, value);
  }

  @Override
  public @Nullable String getTag(@NotNull String key) {
    // TODO [POTEL] need to find a way to transfer data from this wrapper to SpanExporter
    //    return context.getTags().get(key);
    return null;
  }

  @Override
  public boolean isFinished() {
    // TODO [POTEL] find a way to check
    return false;
  }

  @Override
  public void setData(@NotNull String key, @NotNull Object value) {
    // TODO [POTEL] need to find a way to transfer data from this wrapper to SpanExporter
  }

  @Override
  public @Nullable Object getData(@NotNull String key) {
    // TODO [POTEL] need to find a way to transfer data from this wrapper to SpanExporter
    return null;
  }

  @Override
  public void setMeasurement(@NotNull String name, @NotNull Number value) {
    // TODO [POTEL]
  }

  @Override
  public void setMeasurement(
      @NotNull String name, @NotNull Number value, @NotNull MeasurementUnit unit) {
    // TODO [POTEL]
  }

  @Override
  public boolean updateEndDate(@NotNull SentryDate date) {
    return false;
  }

  @Override
  public @NotNull SentryDate getStartDate() {
    return startTimestamp;
  }

  @Override
  public @Nullable SentryDate getFinishDate() {
    // TODO [POTEL] cannot access spandata.getEndEpochNanos
    return null;
  }

  @Override
  public boolean isNoOp() {
    return false;
  }

  @Override
  public @Nullable LocalMetricsAggregator getLocalMetricsAggregator() {
    return null;
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

  @Override
  public void setName(@NotNull String name) {
    setName(name, TransactionNameSource.CUSTOM);
  }

  @Override
  public void setName(@NotNull String name, @NotNull TransactionNameSource nameSource) {
    this.name = name;
    this.nameSource = nameSource;
  }

  @Override
  public @NotNull TransactionNameSource getNameSource() {
    return nameSource;
  }

  @Override
  public @NotNull String getName() {
    return this.name;
  }

  @NotNull
  public SentryId getTraceId() {
    final @Nullable Span otelSpan = getSpan();
    if (otelSpan != null) {
      return new SentryId(otelSpan.getSpanContext().getTraceId());
    } else {
      return SentryId.EMPTY_ID;
    }
  }

  public @NotNull Map<String, Object> getData() {
    //    return data;
    // TODO [POTEL]
    return new HashMap<>();
  }

  @NotNull
  public Map<String, MeasurementValue> getMeasurements() {
    //    return measurements;
    // TODO [POTEL]
    return new HashMap<>();
  }

  @Override
  public @Nullable Boolean isSampled() {
    final @Nullable Span otelSpan = getSpan();
    if (otelSpan != null) {
      return otelSpan.getSpanContext().isSampled();
    }
    return null;
  }

  public @Nullable Boolean isProfileSampled() {
    // we do not support profiling for OpenTelemetry yet
    return false;
  }

  @Override
  public @Nullable TracesSamplingDecision getSamplingDecision() {
    // TODO [POTEL]

    final @Nullable Span otelSpan = getSpan();
    if (otelSpan != null) {
      return new TracesSamplingDecision(otelSpan.getSpanContext().isSampled());
    }

    return null;
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
  @Override
  public @NotNull ISentryLifecycleToken makeCurrent() {
    final @Nullable Span otelSpan = getSpan();
    if (otelSpan != null) {
      final @NotNull Scope otelScope = otelSpan.makeCurrent();
      return new OtelContextSpanStorageToken(otelScope);
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
