package io.sentry;

import org.jetbrains.annotations.Nullable;

/** Represents performance monitoring Span. */
public interface ISpan {
  /**
   * Starts a child Span.
   *
   * @return a new transaction span
   */
  Span startChild();

  /**
   * Returns a string that could be sent as a sentry-trace header.
   *
   * @return SentryTraceHeader.
   */
  SentryTraceHeader toSentryTrace();

  /** Sets span timestamp marking this span as finished. */
  void finish();

  /**
   * Sets span operation.
   *
   * @param op - the operation
   */
  void setOp(@Nullable String op);

  /**
   * Sets span description.
   *
   * @param description - the description.
   */
  void setDescription(@Nullable String description);
}
