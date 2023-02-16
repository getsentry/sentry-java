package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SpanOptions {

  /**
   * If `trimStart` is true, sets the start timestamp of the transaction to the lowest start
   * timestamp of child spans.
   *
   * <p>Currently only relevant for non-root spans.
   */
  private final boolean trimStart;
  /**
   * If `trimEnd` is true, sets the end timestamp of the transaction to the highest timestamp of
   * child spans, trimming the duration of the transaction. This is useful to discard extra time in
   * the idle transactions to trim their duration to children' duration.
   */
  private final boolean trimEnd;

  /**
   * true if the span is considered idle and should be automatically finished when it's parent gets
   * finished
   *
   * <p>Currently only relevant for non-root spans.
   */
  private final boolean isIdle;

  /**
   * The idle time, measured in ms, to wait until the transaction will be finished. The span will
   * use the end timestamp of the last finished span as the endtime for the transaction.
   *
   * <p>When set to {@code null} the transaction must be finished manually.
   *
   * <p>The default is 3 seconds.
   *
   * <p>Currently only relevant for transactions.
   */
  private final @Nullable Long idleTimeout;

  /**
   * When `waitForChildren` is set to `true`, tracer will finish only when both conditions are met
   * (the order of meeting condition does not matter): - tracer itself is finished - all child spans
   * are finished.
   *
   * <p>Currently only relevant for transactions.
   */
  private final boolean waitForChildren;

  public SpanOptions() {
    this(false, false, false, false, null);
  }

  /**
   * @param trimStart true if the start time should be trimmed to the minimum start time of it's
   *     children
   * @param trimEnd true if the end time should be trimmed to the maximum end time of it's children
   * @param isIdle true if this span can be finished automatically
   */
  public SpanOptions(
      final boolean trimStart,
      final boolean trimEnd,
      final boolean isIdle,
      final boolean waitForChildren,
      @Nullable Long idleTimeout) {
    this.trimStart = trimStart;
    this.trimEnd = trimEnd;
    this.isIdle = isIdle;
    this.waitForChildren = waitForChildren;
    this.idleTimeout = idleTimeout;
  }

  public boolean isTrimStart() {
    return trimStart;
  }

  public boolean isTrimEnd() {
    return trimEnd;
  }

  public boolean isIdle() {
    return isIdle;
  }

  @Nullable
  public Long getIdleTimeout() {
    return idleTimeout;
  }

  public boolean isWaitForChildren() {
    return waitForChildren;
  }
}
