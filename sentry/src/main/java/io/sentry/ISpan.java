package io.sentry;

import io.sentry.protocol.Contexts;
import java.util.List;
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
      @NotNull String operation, @Nullable String description, @NotNull SpanOptions spanOptions);

  @ApiStatus.Internal
  @NotNull
  ISpan startChild(@NotNull SpanContext spanContext, @NotNull SpanOptions spanOptions);

  @ApiStatus.Internal
  @NotNull
  ISpan startChild(
      @NotNull String operation,
      @Nullable String description,
      @Nullable SentryDate timestamp,
      @NotNull Instrumenter instrumenter);

  @ApiStatus.Internal
  @NotNull
  ISpan startChild(
      @NotNull String operation,
      @Nullable String description,
      @Nullable SentryDate timestamp,
      @NotNull Instrumenter instrumenter,
      @NotNull SpanOptions spanOptions);

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
   * Returns the trace context.
   *
   * @return a trace context or {@code null} if {@link SentryOptions#isTraceSampling()} is disabled.
   */
  @Nullable
  @ApiStatus.Experimental
  TraceContext traceContext();

  /**
   * Returns the baggage that can be sent as "baggage" header.
   *
   * @return BaggageHeader or {@code null} if {@link SentryOptions#isTraceSampling()} is disabled.
   */
  @Nullable
  @ApiStatus.Experimental
  BaggageHeader toBaggageHeader(@Nullable List<String> thirdPartyBaggageHeaders);

  /** Sets span timestamp marking this span as finished. */
  void finish();

  /**
   * Sets span timestamp marking this span as finished.
   *
   * @param status - the status
   */
  void finish(@Nullable SpanStatus status);

  /**
   * Sets span timestamp marking this span as finished.
   *
   * @param status - the status
   * @param timestamp - the end timestamp
   */
  void finish(@Nullable SpanStatus status, @Nullable SentryDate timestamp);

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
  void setTag(@Nullable String key, @Nullable String value);

  @Nullable
  String getTag(@Nullable String key);

  /**
   * Returns if span has finished.
   *
   * @return if span has finished.
   */
  boolean isFinished();

  /**
   * Sets extra data on span or transaction.
   *
   * @param key the data key
   * @param value the data value
   */
  void setData(@Nullable String key, @Nullable Object value);

  /**
   * Returns extra data from span or transaction.
   *
   * @return the data
   */
  @Nullable
  Object getData(@Nullable String key);

  /**
   * Set a measurement without unit. When setting the measurement without the unit, no formatting
   * will be applied to the measurement value in the Sentry product, and the value will be shown as
   * is.
   *
   * <p>NOTE: Setting a measurement with the same name on the same transaction multiple times only
   * keeps the last value.
   *
   * @param name the name of the measurement
   * @param value the value of the measurement
   */
  void setMeasurement(@NotNull String name, @NotNull Number value);

  /**
   * Set a measurement with specific unit.
   *
   * <p>NOTE: Setting a measurement with the same name on the same transaction multiple times only
   * keeps the last value.
   *
   * @param name the name of the measurement
   * @param value the value of the measurement
   * @param unit the unit the value is measured in
   */
  void setMeasurement(@NotNull String name, @NotNull Number value, @NotNull MeasurementUnit unit);

  /**
   * Updates the end date of the span. Note: This will only update the end date if the span is
   * already finished.
   *
   * @param date the end date to set
   * @return true if the end date was updated, false otherwise
   */
  @ApiStatus.Internal
  boolean updateEndDate(@NotNull SentryDate date);

  /**
   * Returns the start date of this span or transaction.
   *
   * @return the start date
   */
  @ApiStatus.Internal
  @NotNull
  SentryDate getStartDate();

  /**
   * Returns the end date of this span or transaction.
   *
   * @return the end date
   */
  @ApiStatus.Internal
  @Nullable
  SentryDate getFinishDate();

  /**
   * Whether this span instance is a NOOP that doesn't collect information
   *
   * @return true if NOOP
   */
  @ApiStatus.Internal
  boolean isNoOp();

  void setContext(@Nullable String key, @Nullable Object context);

  @NotNull
  Contexts getContexts();

  @Nullable
  Boolean isSampled();

  @Nullable
  TracesSamplingDecision getSamplingDecision();

  @ApiStatus.Internal
  @NotNull
  ISentryLifecycleToken makeCurrent();
}
