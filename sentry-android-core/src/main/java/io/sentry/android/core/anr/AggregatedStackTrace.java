package io.sentry.android.core.anr;

import java.util.Arrays;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class AggregatedStackTrace {
  // the number of frames of the stacktrace
  final int depth;

  // the quality of the stack trace, higher means better (ratio of app frames: 0.0 to 1.0)
  final float quality;

  private final StackTraceElement[] stack;

  // 0 is the most detailed frame in the stacktrace
  private final int stackStartIdx;
  private final int stackEndIdx;

  // the total number of times this exact stacktrace was captured
  int count;

  // first time the stacktrace occurred
  private long startTimeMs;

  // last time the stacktrace occurred
  private long endTimeMs;

  public AggregatedStackTrace(
      final StackTraceElement[] stack,
      final int stackStartIdx,
      final int stackEndIdx,
      final long timestampMs,
      final float quality) {
    this.stack = stack;
    this.stackStartIdx = stackStartIdx;
    this.stackEndIdx = stackEndIdx;
    this.depth = stackEndIdx - stackStartIdx + 1;
    this.startTimeMs = timestampMs;
    this.endTimeMs = timestampMs;
    this.count = 1;
    this.quality = quality;
  }

  public void addOccurrence(final long timestampMs) {
    this.startTimeMs = Math.min(startTimeMs, timestampMs);
    this.endTimeMs = Math.max(endTimeMs, timestampMs);
    this.count++;
  }

  @NotNull
  public StackTraceElement[] getStack() {
    return Arrays.copyOfRange(stack, stackStartIdx, stackEndIdx + 1);
  }
}
