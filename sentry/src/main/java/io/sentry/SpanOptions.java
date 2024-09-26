package io.sentry;

import static io.sentry.SpanContext.DEFAULT_ORIGIN;

import com.jakewharton.nopen.annotation.Open;
import org.jetbrains.annotations.Nullable;

@Open
public class SpanOptions {

  /** The start timestamp of the transaction */
  private @Nullable SentryDate startTimestamp = null;

  /**
   * Gets the startTimestamp
   *
   * @return startTimestamp - the startTimestamp
   */
  public @Nullable SentryDate getStartTimestamp() {
    return startTimestamp;
  }

  /**
   * Sets the startTimestamp
   *
   * @param startTimestamp - the startTimestamp
   */
  public void setStartTimestamp(@Nullable SentryDate startTimestamp) {
    this.startTimestamp = startTimestamp;
  }

  /**
   * If `trimStart` is true, sets the start timestamp of the transaction to the lowest start
   * timestamp of child spans.
   *
   * <p>Currently only relevant for non-root spans.
   */
  private boolean trimStart = false;
  /**
   * If `trimEnd` is true, sets the end timestamp of the transaction to the highest timestamp of
   * child spans, trimming the duration of the transaction. This is useful to discard extra time in
   * the idle transactions to trim their duration to children' duration.
   */
  private boolean trimEnd = false;

  /**
   * true if the span is considered idle and should be automatically finished when it's parent gets
   * finished.
   */
  private boolean isIdle = false;

  protected @Nullable String origin = DEFAULT_ORIGIN;

  public boolean isTrimStart() {
    return trimStart;
  }

  public boolean isTrimEnd() {
    return trimEnd;
  }

  public boolean isIdle() {
    return isIdle;
  }

  public void setTrimStart(boolean trimStart) {
    this.trimStart = trimStart;
  }

  public void setTrimEnd(boolean trimEnd) {
    this.trimEnd = trimEnd;
  }

  public void setIdle(boolean idle) {
    isIdle = idle;
  }

  public @Nullable String getOrigin() {
    return origin;
  }

  public void setOrigin(final @Nullable String origin) {
    this.origin = origin;
  }
}
