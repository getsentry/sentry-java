package io.sentry;

import io.sentry.protocol.SentryId;
import io.sentry.util.Objects;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

@ApiStatus.Internal
public final class Span implements ISpan {

  /** The moment in time when span was started. */
  private final @NotNull SentryDate startTimestamp;

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

  private @Nullable SpanFinishedCallback spanFinishedCallback;

  private final @NotNull Map<String, Object> data = new ConcurrentHashMap<>();

  Span(
      final @NotNull SentryId traceId,
      final @Nullable SpanId parentSpanId,
      final @NotNull SentryTracer transaction,
      final @NotNull String operation,
      final @NotNull IHub hub) {
    this(traceId, parentSpanId, transaction, operation, hub, null, null);
  }

  Span(
      final @NotNull SentryId traceId,
      final @Nullable SpanId parentSpanId,
      final @NotNull SentryTracer transaction,
      final @NotNull String operation,
      final @NotNull IHub hub,
      final @Nullable SentryDate startTimestamp,
      final @Nullable SpanFinishedCallback spanFinishedCallback) {
    this.context =
        new SpanContext(
            traceId, new SpanId(), operation, parentSpanId, transaction.getSamplingDecision());
    this.transaction = Objects.requireNonNull(transaction, "transaction is required");
    this.hub = Objects.requireNonNull(hub, "hub is required");
    this.spanFinishedCallback = spanFinishedCallback;
    if (startTimestamp != null) {
      this.startTimestamp = startTimestamp;
    } else {
      this.startTimestamp = hub.getOptions().getDateProvider().now();
    }
  }

  @VisibleForTesting
  public Span(
      final @NotNull TransactionContext context,
      final @NotNull SentryTracer sentryTracer,
      final @NotNull IHub hub,
      final @Nullable SentryDate startTimestamp) {
    this.context = Objects.requireNonNull(context, "context is required");
    this.transaction = Objects.requireNonNull(sentryTracer, "sentryTracer is required");
    this.hub = Objects.requireNonNull(hub, "hub is required");
    this.spanFinishedCallback = null;
    if (startTimestamp != null) {
      this.startTimestamp = startTimestamp;
    } else {
      this.startTimestamp = hub.getOptions().getDateProvider().now();
    }
  }

  public @NotNull SentryDate getStartDate() {
    return startTimestamp;
  }

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
      final @NotNull Instrumenter instrumenter) {
    if (finished.get()) {
      return NoOpSpan.getInstance();
    }

    return transaction.startChild(
        context.getSpanId(), operation, description, timestamp, instrumenter);
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
    if (throwable != null) {
      hub.setSpanContext(throwable, this, this.transaction.getName());
    }
    if (spanFinishedCallback != null) {
      spanFinishedCallback.execute(this);
    }
  }

  @Override
  public void setOperation(final @NotNull String operation) {
    if (finished.get()) {
      return;
    }

    this.context.setOperation(operation);
  }

  @Override
  public @NotNull String getOperation() {
    return this.context.getOperation();
  }

  @Override
  public void setDescription(final @Nullable String description) {
    if (finished.get()) {
      return;
    }

    this.context.setDescription(description);
  }

  @Override
  public @Nullable String getDescription() {
    return this.context.getDescription();
  }

  @Override
  public void setStatus(final @Nullable SpanStatus status) {
    if (finished.get()) {
      return;
    }

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
    if (finished.get()) {
      return;
    }

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
    if (finished.get()) {
      return;
    }

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
  public void setData(@NotNull String key, @NotNull Object value) {
    if (finished.get()) {
      return;
    }

    data.put(key, value);
  }

  @Override
  public @Nullable Object getData(@NotNull String key) {
    return data.get(key);
  }

  @Override
  public void setMeasurement(@NotNull String name, @NotNull Number value) {
    this.transaction.setMeasurement(name, value);
  }

  @Override
  public void setMeasurement(
      @NotNull String name, @NotNull Number value, @NotNull MeasurementUnit unit) {
    this.transaction.setMeasurement(name, value, unit);
  }

  @Override
  public boolean isNoOp() {
    return false;
  }

  void setSpanFinishedCallback(final @Nullable SpanFinishedCallback callback) {
    this.spanFinishedCallback = callback;
  }
}
