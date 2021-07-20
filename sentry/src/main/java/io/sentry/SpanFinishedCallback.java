package io.sentry;

import org.jetbrains.annotations.NotNull;

interface SpanFinishedCallback {
  /**
   * Called when observed span finishes.
   *
   * @param span the span that has finished.
   */
  void onSpanFinished(final @NotNull Span span);
}
