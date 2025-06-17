package io.sentry.android.core.anr;

class StackTrace {

  final StackTraceElement[] stack;

  /** The timestamp in unix time when this stack trace was captured. */
  final long timestampMs;

  public StackTrace(final long timestampMs, final StackTraceElement[] stack) {
    this.timestampMs = timestampMs;
    this.stack = stack;
  }
}
