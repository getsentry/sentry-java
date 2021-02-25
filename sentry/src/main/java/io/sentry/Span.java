package io.sentry;

import io.sentry.protocol.SentryId;
import io.sentry.util.Objects;
import java.util.Date;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class Span implements ISpan {

  /** The moment in time when span was started. */
  private final @NotNull Date startTimestamp;
  /** The moment in time when span has ended. */
  private @Nullable Date timestamp;

  private final @NotNull SpanContext context;

  /**
   * A transaction this span is attached to. Marked as transient to be ignored during JSON
   * serialization.
   */
  private final transient @NotNull SentryTracer transaction;

  /** A throwable thrown during the execution of the span. */
  private transient @Nullable Throwable throwable;

  private final transient @NotNull IHub hub;

  Span(
      final @NotNull SentryId traceId,
      final @Nullable SpanId parentSpanId,
      final @NotNull SentryTracer transaction,
      final @NotNull String operation,
      final @NotNull IHub hub) {
    this.context =
        new SpanContext(traceId, new SpanId(), operation, parentSpanId, transaction.isSampled());
    this.transaction = Objects.requireNonNull(transaction, "transaction is required");
    this.startTimestamp = DateUtils.getCurrentDateTime();
    this.hub = Objects.requireNonNull(hub, "hub is required");
  }

  Span(
      final @NotNull TransactionContext context,
      final @NotNull SentryTracer sentryTracer,
      final @NotNull IHub hub) {
    this.context = Objects.requireNonNull(context, "context is required");
    this.transaction = Objects.requireNonNull(sentryTracer, "sentryTracer is required");
    this.hub = Objects.requireNonNull(hub, "hub is required");
    this.startTimestamp = DateUtils.getCurrentDateTime();
  }

  public @NotNull Date getStartTimestamp() {
    return startTimestamp;
  }

  public @Nullable Date getTimestamp() {
    return timestamp;
  }

  @Override
  public void setName(final @Nullable String name) {
    this.setTag(ISpan.NAME_TAG, name);
  }

  @Override
  public @Nullable String getName() {
    return this.getTag(ISpan.NAME_TAG);
  }

  @Override
  public @NotNull ISpan startChild(final @NotNull String operation) {
    return this.startChild(operation, null);
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
  public void finish() {
    timestamp = DateUtils.getCurrentDateTime();
    if (throwable != null) {
      hub.setSpanContext(throwable, this);
    }
  }

  @Override
  public void finish(final @Nullable SpanStatus status) {
    this.context.setStatus(status);
    this.finish();
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
  public @NotNull SpanContext getContext() {
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
    return this.timestamp != null;
  }

  @Override
  public @Nullable Boolean isSampled() {
    return context.getSampled();
  }

  @Override
  public ISpan getLatestActiveSpan() {
    return isFinished() ? null : this;
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
  SentryId getTraceId() {
    return context.getTraceId();
  }

  @NotNull
  SpanId getSpanId() {
    return context.getSpanId();
  }

  @Nullable
  SpanId getParentSpanId() {
    return context.getParentSpanId();
  }
}
