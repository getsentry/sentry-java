package io.sentry;

/** Represents performance monitoring Span. */
public interface ISpan {
  /**
   * Starts a child Span.
   *
   * @return a new transaction span
   */
  Span startChild();

  /**
   * Returns a string that could be sent as a sentry-trace header.
   *
   * @return string containing sentry-trace header.
   */
  String toTraceparent();

  /** Sets span timestamp marking this span as finished. */
  void finish();
}
