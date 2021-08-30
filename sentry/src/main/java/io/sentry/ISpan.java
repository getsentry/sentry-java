package io.sentry;

import java.util.Date;
import org.jetbrains.annotations.ApiStatus;
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

  @ApiStatus.Internal
  @NotNull
  ISpan startChild(
      @NotNull String operation, @Nullable String description, @Nullable Date timestamp);

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
   * Returns the trace information that could be sent as a sentry-trace header.
   *
   * @return SentryTraceHeader.
   */
  @NotNull
  SentryTraceHeader toSentryTrace();

  /**
   * Returns the trace state information. @see <a
   * href="https://develop.sentry.dev/sdk/trace-context/">Trace Context</a>.
   *
   * @return a trace state or {@code null} if {@link SentryOptions#isTraceSampling()} is disabled.
   */
  @Nullable
  @ApiStatus.Experimental
  TraceState traceState();

  /**
   * Returns the trace state that can be sent as a "tracestate" header.
   *
   * @return TraceStateHeader or {@code null} if {@link SentryOptions#isTraceSampling()} is
   *     disabled.
   */
  @Nullable
  @ApiStatus.Experimental
  TraceStateHeader toTraceStateHeader();

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
  @NotNull
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
  @Nullable
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

  @Nullable
  String getTag(@NotNull String key);

  /**
   * Returns if span has finished.
   *
   * @return if span has finished.
   */
  boolean isFinished();
}
