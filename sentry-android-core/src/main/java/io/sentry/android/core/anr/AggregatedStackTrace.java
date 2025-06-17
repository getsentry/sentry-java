package io.sentry.android.core.anr;

import java.util.Arrays;

class AggregatedStackTrace {
  // the number of frames of the stacktrace
  final int depth;

  // the quality of the stack trace, higher means better
  final int quality;

  // the full stacktrace itself this aggregate refers to
  // index 0 is the most detailed frame in the stacktrace
  private final StackTraceElement[] stack;

  // the start index within stack for this aggregate
  private final int stackStartIdx;

  // the end index (inclusive) within stack for this aggregate
  private final int stackEndIdx;

  // the total number of times this exact stacktrace was captured
  int count;

  // the first time the stacktrace occurred
  private long startTimeMs;

  // the last time the stacktrace occurred
  private long endTimeMs;

  public AggregatedStackTrace(
      final StackTraceElement[] stack,
      final int stackStartIdx,
      final int stackEndIdx,
      final long timestampMs,
      final int quality) {
    this.stack = stack;
    this.stackStartIdx = stackStartIdx;
    this.stackEndIdx = stackEndIdx;
    this.depth = stackEndIdx - stackStartIdx;
    this.startTimeMs = timestampMs;
    this.endTimeMs = timestampMs;
    this.count = 1;
    this.quality = quality;
  }

  public void add(long timestampMs) {
    this.startTimeMs = Math.min(startTimeMs, timestampMs);
    this.endTimeMs = Math.max(endTimeMs, timestampMs);
    this.count++;
  }

  public StackTraceElement[] getStack() {
    return Arrays.copyOfRange(stack, stackStartIdx, stackEndIdx + 1);
  }

  public Exception toException() {
    final StackTraceElement[] stackTrace = getStack();
    final String message;
    if (stackTrace.length > 0) {
      final StackTraceElement stackTraceElement = stackTrace[0];
      message = stackTraceElement.getClassName() + " " + stackTraceElement.getMethodName();
    } else {
      message = "Watchdog ANR";
    }
    final Exception e = new AnrException(message);
    e.setStackTrace(stackTrace);
    return e;
  }
}
