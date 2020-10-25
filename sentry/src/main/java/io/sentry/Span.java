package io.sentry;

import io.sentry.protocol.SentryId;
import java.util.Date;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Span extends SpanContext implements ISpan {

  /** The moment in time when span was started. */
  private final @NotNull Date startTimestamp;
  /** The moment in time when span has ended. */
  private @Nullable Date timestamp;

  /** Span id. */
  private final @NotNull SpanId spanId;

  /** Id of a parent span. */
  private final @NotNull SpanId parentSpanId;

  /**
   * Trace id - same value as {@link Transaction#getContexts()#traceId} from the transaction span is
   * attached to.
   */
  private final @NotNull SentryId traceId;

  /**
   * A transaction this span is attached to. Marked as transient to be ignored during JSON
   * serialization.
   */
  private final transient @NotNull Transaction transaction;

  Span(
      final @NotNull SentryId traceId,
      final @NotNull SpanId parentSpanId,
      final @NotNull Transaction transaction) {
    this.transaction = transaction;
    this.traceId = traceId;
    this.parentSpanId = parentSpanId;
    this.spanId = new SpanId();
    this.startTimestamp = DateUtils.getCurrentDateTime();
  }

  public @NotNull Date getStartTimestamp() {
    return startTimestamp;
  }

  public @Nullable Date getTimestamp() {
    return timestamp;
  }

  public @NotNull SpanId getParentSpanId() {
    return parentSpanId;
  }

  @Override
  public @NotNull Span startChild() {
    return transaction.startChild(spanId);
  }

  @Override
  public @NotNull String toTraceparent() {
    return String.format("%s-%s", traceId, spanId);
  }

  public @NotNull SpanId getSpanId() {
    return spanId;
  }

  public @NotNull SentryId getTraceId() {
    return traceId;
  }

  @Override
  public void finish() {
    this.timestamp = DateUtils.getCurrentDateTime();
  }
}
