package io.sentry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Represents performance monitoring Span. */
public interface ISpan {
  /**
   * Starts a child Span.
   *
   * @param operation - new span operation name
   * @return a new transaction span
   */
  @NotNull
  ISpan startChild(@NotNull String operation);

  /**
   * Starts a child Span.
   *
   * @param operation - new span operation name
   * @param description - new span description name
   * @return a new transaction span
   */
  @NotNull
  ISpan startChild(@NotNull String operation, @Nullable String description);

  /**
   * Returns a string that could be sent as a sentry-trace header.
   *
   * @return SentryTraceHeader.
   */
  @NotNull
  SentryTraceHeader toSentryTrace();

  /** Sets span timestamp marking this span as finished. */
  void finish();

  /**
   * Sets span timestamp marking this span as finished.
   *
   * @param status - the status
   */
  void finish(@Nullable SpanStatus status);

  /**
   * Sets span operation.
   *
   * @param operation - the operation
   */
  void setOperation(@NotNull String operation);

  /**
   * Returns the span operation.
   *
   * @return the operation
   */
  @Nullable
  String getOperation();

  /**
   * Sets span description.
   *
   * @param description - the description.
   */
  void setDescription(@Nullable String description);

  /**
   * Returns the span description.
   *
   * @return the description
   */
  @NotNull
  String getDescription();

  /**
   * Sets span status.
   *
   * @param status - the status.
   */
  void setStatus(@Nullable SpanStatus status);

  /**
   * Returns the span status
   *
   * @return the status
   */
  @Nullable
  SpanStatus getStatus();

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

  /**
   * Returns if span has finished.
   *
   * @return if span has finished.
   */
  boolean isFinished();
}
