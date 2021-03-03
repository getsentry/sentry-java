package io.sentry;

import io.sentry.protocol.SentryId;
import io.sentry.util.Objects;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Span extends SpanContext implements ISpan {

  /** The moment in time when span was started. */
  private final @NotNull Date startTimestamp;
  /** The moment in time when span has ended. */
  private @Nullable Date timestamp;

  /**
   * A transaction this span is attached to. Marked as transient to be ignored during JSON
   * serialization.
   */
  private final transient @NotNull SentryTransaction transaction;

  /** A throwable thrown during the execution of the span. */
  private transient @Nullable Throwable throwable;

  private final transient @NotNull IHub hub;

  private final @NotNull AtomicBoolean finished = new AtomicBoolean(false);

  Span(
      final @NotNull SentryId traceId,
      final @NotNull SpanId parentSpanId,
      final @NotNull SentryTransaction transaction,
      final @NotNull String operation,
      final @NotNull IHub hub) {
    super(traceId, new SpanId(), operation, parentSpanId, transaction.isSampled());
    this.transaction = Objects.requireNonNull(transaction, "transaction is required");
    this.startTimestamp = DateUtils.getCurrentDateTime();
    this.hub = Objects.requireNonNull(hub, "hub is required");
  }

  public @NotNull Date getStartTimestamp() {
    return startTimestamp;
  }

  public @Nullable Date getTimestamp() {
    return timestamp;
  }

  @Override
  public @NotNull ISpan startChild(final @NotNull String operation) {
    return this.startChild(operation, null);
  }

  @Override
  public @NotNull ISpan startChild(
      final @NotNull String operation, final @Nullable String description) {
    return transaction.startChild(super.getSpanId(), operation, description);
  }

  @Override
  public @NotNull SentryTraceHeader toSentryTrace() {
    return new SentryTraceHeader(getTraceId(), getSpanId(), getSampled());
  }

  @Override
  public void finish() {
    this.finish(this.status);
  }

  @Override
  public void finish(@Nullable SpanStatus status) {
    // the span can be finished only once
    if (!finished.compareAndSet(false, true)) {
      return;
    }

    this.status = status;
    timestamp = DateUtils.getCurrentDateTime();
    if (throwable != null) {
      hub.setSpanContext(throwable, this);
    }
  }

  @Override
  public @NotNull SpanContext getSpanContext() {
    return this;
  }

  @Override
  public boolean isFinished() {
    return finished.get();
  }

  @Override
  public void setThrowable(final @Nullable Throwable throwable) {
    this.throwable = throwable;
  }

  @Override
  public @Nullable Throwable getThrowable() {
    return throwable;
  }
}
