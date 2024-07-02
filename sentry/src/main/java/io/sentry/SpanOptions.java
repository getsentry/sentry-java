package io.sentry;

import com.jakewharton.nopen.annotation.Open;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
@Open
public class SpanOptions {

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
}
