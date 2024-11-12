package io.sentry.android.core.anr;

class StackTrace {

  final StackTraceElement[] stack;
  final long timestampMs;

  public StackTrace(final long timestampMs, final StackTraceElement[] stack) {
    this.timestampMs = timestampMs;
    this.stack = stack;
  }
}
