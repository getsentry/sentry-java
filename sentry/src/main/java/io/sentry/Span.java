package io.sentry;

import io.sentry.protocol.SentryId;
import io.sentry.util.Objects;
import java.util.Date;
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
  private final @NotNull Date startTimestamp;

  /**
   * The moment in time when span has started, read from {@link System#nanoTime()}. Can be {@code
   * null} when {@link #startTimestamp} was provided to span constructor.
   */
  private final @Nullable Long startNanos;

  /** The moment in time when span has finished, read from {@link System#nanoTime()}. */
  private @Nullable Long endNanos;

  /** The moment in time when span has ended. */
  private @Nullable Double timestamp;

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

  private final @Nullable SpanFinishedCallback spanFinishedCallback;

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
      final @Nullable Date startTimestamp,
      final @Nullable SpanFinishedCallback spanFinishedCallback) {
    this.context =
        new SpanContext(traceId, new SpanId(), operation, parentSpanId, transaction.isSampled());
    this.transaction = Objects.requireNonNull(transaction, "transaction is required");
    this.hub = Objects.requireNonNull(hub, "hub is required");
    this.spanFinishedCallback = spanFinishedCallback;
    if (startTimestamp != null) {
      this.startTimestamp = startTimestamp;
      this.startNanos = null;
    } else {
      this.startTimestamp = DateUtils.getCurrentDateTime();
      this.startNanos = System.nanoTime();
    }
  }

  @VisibleForTesting
  public Span(
      final @NotNull TransactionContext context,
      final @NotNull SentryTracer sentryTracer,
      final @NotNull IHub hub,
      final @Nullable Date startTimestamp) {
    this.context = Objects.requireNonNull(context, "context is required");
    this.transaction = Objects.requireNonNull(sentryTracer, "sentryTracer is required");
    this.hub = Objects.requireNonNull(hub, "hub is required");
    this.spanFinishedCallback = null;
    if (startTimestamp != null) {
      this.startTimestamp = startTimestamp;
      this.startNanos = null;
    } else {
      this.startTimestamp = DateUtils.getCurrentDateTime();
      this.startNanos = System.nanoTime();
    }
  }

  public @NotNull Date getStartTimestamp() {
    return startTimestamp;
  }

  public @Nullable Double getTimestamp() {
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
      final @Nullable Date timestamp) {
    return transaction.startChild(context.getSpanId(), operation, description, timestamp);
  }

  @Override
  public @NotNull ISpan startChild(
      final @NotNull String operation, final @Nullable String description) {
    return transaction.startChild(context.getSpanId(), operation, description);
  }

  @Override
  public @NotNull SentryTraceHeader toSentryTrace() {
    return new SentryTraceHeader(context.getTraceId(), context.getSpanId(), context.getSampled());
  }

  @Override
  public @Nullable TraceState traceState() {
    return transaction.traceState();
  }

  @Override
  public @Nullable TraceStateHeader toTraceStateHeader() {
    return transaction.toTraceStateHeader();
  }

  @Override
  public void finish() {
    this.finish(this.context.getStatus());
  }

  @Override
  public void finish(@Nullable SpanStatus status) {
    if (finish(status, DateUtils.dateToSeconds(DateUtils.getCurrentDateTime()))) {
      this.endNanos = System.nanoTime();
    }
  }

  /**
   * Used to finish unfinished spans by {@link SentryTracer}.
   *
   * @param status - status to finish span with
   * @param timestamp - the root span timestamp.
   * @return true if finishing was successful, false if span has been already finished
   */
  boolean finish(@Nullable SpanStatus status, @NotNull Double timestamp) {
    // the span can be finished only once
    if (!finished.compareAndSet(false, true)) {
      return false;
    }

    this.context.setStatus(status);
    this.timestamp = timestamp;
    if (throwable != null) {
      hub.setSpanContext(throwable, this, this.transaction.getName());
    }
    if (spanFinishedCallback != null) {
      spanFinishedCallback.execute(this);
    }
    return true;
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
  public void setData(@NotNull String key, @NotNull Object value) {
    data.put(key, value);
  }

  @Override
  public @Nullable Object getData(@NotNull String key) {
    return data.get(key);
  }

  /**
   * Returns high precision span finish time represented as {@link Double}.
   *
   * @return high precision span finish time
   */
  @SuppressWarnings("JavaUtilDate")
  public @Nullable Double getHighPrecisionTimestamp() {
    final Double duration = getDurationInMillis();
    if (duration != null) {
      return DateUtils.millisToSeconds(startTimestamp.getTime() + duration);
    } else if (timestamp != null) {
      return timestamp;
    } else {
      return null;
    }
  }

  /**
   * Returns span duration in milliseconds or {@code null} when {@link #startNanos} is not set.
   *
   * @return span duration in milliseconds
   */
  private @Nullable Double getDurationInMillis() {
    if (startNanos != null && endNanos != null) {
      return DateUtils.nanosToMillis(endNanos - startNanos);
    } else {
      return null;
    }
  }
}
