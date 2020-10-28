package io.sentry;

import io.sentry.protocol.SentryId;
import java.util.Date;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Span extends TraceContext implements ISpan {

  /** The moment in time when span was started. */
  private final @NotNull Date startTimestamp;
  /** The moment in time when span has ended. */
  private @Nullable Date timestamp;

  /**
   * A transaction this span is attached to. Marked as transient to be ignored during JSON
   * serialization.
   */
  private final transient @NotNull Transaction transaction;

  Span(
      final @NotNull SentryId traceId,
      final @NotNull SpanId parentSpanId,
      final @NotNull Transaction transaction) {
    super(traceId, new SpanId(), parentSpanId);
    this.transaction = transaction;
    this.startTimestamp = DateUtils.getCurrentDateTime();
  }

  public @NotNull Date getStartTimestamp() {
    return startTimestamp;
  }

  public @Nullable Date getTimestamp() {
    return timestamp;
  }

  @Override
  public @NotNull Span startChild() {
    return transaction.startChild(super.getSpanId());
  }

  @Override
  public void finish() {
    this.timestamp = DateUtils.getCurrentDateTime();
  }

  boolean isFinished() {
    return this.timestamp != null;
  }
}
