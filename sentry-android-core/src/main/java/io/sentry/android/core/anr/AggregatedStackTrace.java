package io.sentry.android.core.anr;

import java.util.Arrays;

class AggregatedStackTrace {
  final int quality;
  private final StackTraceElement[] stack;
  private final int stackStartIdx;
  private final int stackEndIdx;
  int count;
  private long startTimeMs;
  private long endTimeMs;

  public AggregatedStackTrace(
      StackTraceElement[] stack, int stackStartIdx, int stackEndIdx, long timestampMs) {
    this.stack = stack;
    this.stackStartIdx = stackStartIdx;
    this.stackEndIdx = stackEndIdx;
    this.quality = stackEndIdx - stackStartIdx;
    this.startTimeMs = timestampMs;
    this.endTimeMs = timestampMs;
    this.count = 1;
  }

  public void add(long timestampMs) {
    this.startTimeMs = Math.min(startTimeMs, timestampMs);
    this.endTimeMs = Math.max(endTimeMs, timestampMs);
    this.count++;
  }

  public StackTraceElement[] getStack() {
    return Arrays.copyOfRange(stack, stackStartIdx, stackEndIdx + 1);
  }
}
