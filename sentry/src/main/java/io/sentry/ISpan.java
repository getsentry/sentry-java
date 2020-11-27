package io.sentry;

import org.jetbrains.annotations.NotNull;
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
   * Starts a child Span.
   *
   * @param operation - new span operation name
   * @param description - new span description name
   * @return a new transaction span
   */
  Span startChild(String operation, String description);

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
   * @param operation - the operation
   */
  void setOperation(@Nullable String operation);

  /**
   * Sets span description.
   *
   * @param description - the description.
   */
  void setDescription(@Nullable String description);

  /**
   * Sets span status.
   *
   * @param status - the status.
   */
  void setStatus(@Nullable SpanStatus status);

  /**
   * Sets the throwable that was thrown during the execution of the span.
   *
   * @param throwable - the throwable.
   */
  void setThrowable(@Nullable Throwable throwable);

  /**
   * Gets the throwable that was thrown during the execution of the span.
   *
   * @return throwable or {@code null} if none
   */
  @Nullable
  Throwable getThrowable();

  /**
   * Gets the span context.
   *
   * @return the span context
   */
  @NotNull
  SpanContext getSpanContext();

  /**
   * Sets the tag on span or transaction.
   *
   * @param key the tag key
   * @param value the tag value
   */
  void setTag(@NotNull String key, @NotNull String value);
}
