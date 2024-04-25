package io.sentry;

import io.sentry.metrics.LocalMetricsAggregator;
import io.sentry.protocol.MeasurementValue;
import io.sentry.protocol.SentryId;
import io.sentry.util.LazyEvaluator;
import io.sentry.util.Objects;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class Span implements ISpan {

  /** The moment in time when span was started. */
  private @NotNull SentryDate startTimestamp;

  /** The moment in time when span has ended. */
  private @Nullable SentryDate timestamp;

  private final @NotNull SpanContext context;

  /**
   * A transaction this span is attached to. Marked as transient to be ignored during JSON
   * serialization.
   */
  private final @NotNull SentryTracer transaction;

  /** A throwable thrown during the execution of the span. */
  private @Nullable Throwable throwable;

  private final @NotNull IHub hub;

  private final @NotNull AtomicBoolean finished = new AtomicBoolean(false);

  private final @NotNull SpanOptions options;

  private @Nullable SpanFinishedCallback spanFinishedCallback;

  private final @NotNull Map<String, Object> data = new ConcurrentHashMap<>();
  private final @NotNull Map<String, MeasurementValue> measurements = new ConcurrentHashMap<>();

  @SuppressWarnings("Convert2MethodRef") // older AGP versions do not support method references
  private final @NotNull LazyEvaluator<LocalMetricsAggregator> metricsAggregator =
      new LazyEvaluator<>(() -> new LocalMetricsAggregator());

  Span(
      final @NotNull SentryId traceId,
      final @Nullable SpanId parentSpanId,
      final @NotNull SentryTracer transaction,
      final @NotNull String operation,
      final @NotNull IHub hub) {
    this(traceId, parentSpanId, transaction, operation, hub, null, new SpanOptions(), null);
  }

  Span(
      final @NotNull SentryId traceId,
      final @Nullable SpanId parentSpanId,
      final @NotNull SentryTracer transaction,
      final @NotNull String operation,
      final @NotNull IHub hub,
      final @Nullable SentryDate startTimestamp,
      final @NotNull SpanOptions options,
      final @Nullable SpanFinishedCallback spanFinishedCallback) {
    this.context =
        new SpanContext(
            traceId, new SpanId(), operation, parentSpanId, transaction.getSamplingDecision());
    this.transaction = Objects.requireNonNull(transaction, "transaction is required");
    this.hub = Objects.requireNonNull(hub, "hub is required");
    this.options = options;
    this.spanFinishedCallback = spanFinishedCallback;
    if (startTimestamp != null) {
      this.startTimestamp = startTimestamp;
    } else {
      this.startTimestamp = hub.getOptions().getDateProvider().now();
    }
  }

  public Span(
      final @NotNull TransactionContext context,
      final @NotNull SentryTracer sentryTracer,
      final @NotNull IHub hub,
      final @Nullable SentryDate startTimestamp,
      final @NotNull SpanOptions options) {
    this.context = Objects.requireNonNull(context, "context is required");
    this.transaction = Objects.requireNonNull(sentryTracer, "sentryTracer is required");
    this.hub = Objects.requireNonNull(hub, "hub is required");
    this.spanFinishedCallback = null;
    if (startTimestamp != null) {
      this.startTimestamp = startTimestamp;
    } else {
      this.startTimestamp = hub.getOptions().getDateProvider().now();
    }
    this.options = options;
  }

  @Override
  public @NotNull SentryDate getStartDate() {
    return startTimestamp;
  }

  @Override
  public @Nullable SentryDate getFinishDate() {
    return timestamp;
  }

  @Override
  public @NotNull ISpan startChild(final @NotNull String operation) {
    return this.startChild(operation, (String) null);
  }

  @Override
  public @NotNull ISpan startChild(
      final @NotNull String operation,
      final @Nullable String description,
      final @Nullable SentryDate timestamp,
      final @NotNull Instrumenter instrumenter,
      @NotNull SpanOptions spanOptions) {
    if (finished.get()) {
      return NoOpSpan.getInstance();
    }

    return transaction.startChild(
        context.getSpanId(), operation, description, timestamp, instrumenter, spanOptions);
  }

  @Override
  public @NotNull ISpan startChild(
      final @NotNull String operation, final @Nullable String description) {
    if (finished.get()) {
      return NoOpSpan.getInstance();
    }

    return transaction.startChild(context.getSpanId(), operation, description);
  }

  @Override
  public @NotNull ISpan startChild(
      @NotNull String operation, @Nullable String description, @NotNull SpanOptions spanOptions) {
    if (finished.get()) {
      return NoOpSpan.getInstance();
    }
    return transaction.startChild(context.getSpanId(), operation, description, spanOptions);
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
  public @NotNull SentryTraceHeader toSentryTrace() {
    return new SentryTraceHeader(context.getTraceId(), context.getSpanId(), context.getSampled());
  }

  @Override
  public @Nullable TraceContext traceContext() {
    return transaction.traceContext();
  }

  @Override
  public @Nullable BaggageHeader toBaggageHeader(@Nullable List<String> thirdPartyBaggageHeaders) {
    return transaction.toBaggageHeader(thirdPartyBaggageHeaders);
  }

  @Override
  public void finish() {
    this.finish(this.context.getStatus());
  }

  @Override
  public void finish(@Nullable SpanStatus status) {
    finish(status, hub.getOptions().getDateProvider().now());
  }

  /**
   * Used to finish unfinished spans by {@link SentryTracer}.
   *
   * @param status - status to finish span with
   * @param timestamp - the root span timestamp.
   */
  @Override
  public void finish(final @Nullable SpanStatus status, final @Nullable SentryDate timestamp) {
    // the span can be finished only once
    if (!finished.compareAndSet(false, true)) {
      return;
    }

    this.context.setStatus(status);
    this.timestamp = timestamp == null ? hub.getOptions().getDateProvider().now() : timestamp;
    if (options.isTrimStart() || options.isTrimEnd()) {
      @Nullable SentryDate minChildStart = null;
      @Nullable SentryDate maxChildEnd = null;

      // The root span should be trimmed based on all children, but the other spans, like the
      // jetpack composition should be trimmed based on its direct children only
      final @NotNull List<Span> children =
          transaction.getRoot().getSpanId().equals(getSpanId())
              ? transaction.getChildren()
              : getDirectChildren();
      for (final Span child : children) {
        if (minChildStart == null || child.getStartDate().isBefore(minChildStart)) {
          minChildStart = child.getStartDate();
        }
        if (maxChildEnd == null
            || (child.getFinishDate() != null && child.getFinishDate().isAfter(maxChildEnd))) {
          maxChildEnd = child.getFinishDate();
        }
      }
      if (options.isTrimStart()
          && minChildStart != null
          && startTimestamp.isBefore(minChildStart)) {
        updateStartDate(minChildStart);
      }
      if (options.isTrimEnd()
          && maxChildEnd != null
          && (this.timestamp == null || this.timestamp.isAfter(maxChildEnd))) {
        updateEndDate(maxChildEnd);
      }
    }

    if (throwable != null) {
      hub.setSpanContext(throwable, this, this.transaction.getName());
    }
    if (spanFinishedCallback != null) {
      spanFinishedCallback.execute(this);
    }
  }

  @Override
  public void setOperation(final @NotNull String operation) {
    this.context.setOperation(operation);
  }

  @Override
  public @NotNull String getOperation() {
    return this.context.getOperation();
  }

  @Override
  public void setDescription(final @Nullable String description) {
    this.context.setDescription(description);
  }

  @Override
  public @Nullable String getDescription() {
    return this.context.getDescription();
  }

  @Override
  public void setStatus(final @Nullable SpanStatus status) {
    this.context.setStatus(status);
  }

  @Override
  public @Nullable SpanStatus getStatus() {
    return this.context.getStatus();
  }

  @Override
  public @NotNull SpanContext getSpanContext() {
    return context;
  }

  @Override
  public void setTag(final @NotNull String key, final @NotNull String value) {
    this.context.setTag(key, value);
  }

  @Override
  public @Nullable String getTag(@NotNull String key) {
    return context.getTags().get(key);
  }

  @Override
  public boolean isFinished() {
    return finished.get();
  }

  public @NotNull Map<String, Object> getData() {
    return data;
  }

  public @Nullable Boolean isSampled() {
    return context.getSampled();
  }

  public @Nullable Boolean isProfileSampled() {
    return context.getProfileSampled();
  }

  public @Nullable TracesSamplingDecision getSamplingDecision() {
    return context.getSamplingDecision();
  }

  @Override
  public void setThrowable(final @Nullable Throwable throwable) {
    this.throwable = throwable;
  }

  @Override
  public @Nullable Throwable getThrowable() {
    return throwable;
  }

  @NotNull
  public SentryId getTraceId() {
    return context.getTraceId();
  }

  public @NotNull SpanId getSpanId() {
    return context.getSpanId();
  }

  public @Nullable SpanId getParentSpanId() {
    return context.getParentSpanId();
  }

  public Map<String, String> getTags() {
    return context.getTags();
  }

  @Override
  public void setData(final @NotNull String key, final @NotNull Object value) {
    data.put(key, value);
  }

  @Override
  public @Nullable Object getData(final @NotNull String key) {
    return data.get(key);
  }

  @Override
  public void setMeasurement(final @NotNull String name, final @NotNull Number value) {
    if (isFinished()) {
      hub.getOptions()
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "The span is already finished. Measurement %s cannot be set",
              name);
      return;
    }
    this.measurements.put(name, new MeasurementValue(value, null));
    // We set the measurement in the transaction, too, but we have to check if this is the root span
    // of the transaction, to avoid an infinite recursion
    if (transaction.getRoot() != this) {
      transaction.setMeasurementFromChild(name, value);
    }
  }

  @Override
  public void setMeasurement(
      final @NotNull String name,
      final @NotNull Number value,
      final @NotNull MeasurementUnit unit) {
    if (isFinished()) {
      hub.getOptions()
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "The span is already finished. Measurement %s cannot be set",
              name);
      return;
    }
    this.measurements.put(name, new MeasurementValue(value, unit.apiName()));
    // We set the measurement in the transaction, too, but we have to check if this is the root span
    // of the transaction, to avoid an infinite recursion
    if (transaction.getRoot() != this) {
      transaction.setMeasurementFromChild(name, value, unit);
    }
  }

  @NotNull
  public Map<String, MeasurementValue> getMeasurements() {
    return measurements;
  }

  @Override
  public boolean updateEndDate(final @NotNull SentryDate date) {
    if (this.timestamp != null) {
      this.timestamp = date;
      return true;
    }
    return false;
  }

  @Override
  public boolean isNoOp() {
    return false;
  }

  @Override
  public @NotNull LocalMetricsAggregator getLocalMetricsAggregator() {
    return metricsAggregator.getValue();
  }

  void setSpanFinishedCallback(final @Nullable SpanFinishedCallback callback) {
    this.spanFinishedCallback = callback;
  }

  private void updateStartDate(@NotNull SentryDate date) {
    this.startTimestamp = date;
  }

  @NotNull
  SpanOptions getOptions() {
    return options;
  }

  @NotNull
  private List<Span> getDirectChildren() {
    final List<Span> children = new ArrayList<>();
    final Iterator<Span> iterator = transaction.getSpans().iterator();

    while (iterator.hasNext()) {
      final Span span = iterator.next();
      if (span.getParentSpanId() != null && span.getParentSpanId().equals(getSpanId())) {
        children.add(span);
      }
    }
    return children;
  }
}
