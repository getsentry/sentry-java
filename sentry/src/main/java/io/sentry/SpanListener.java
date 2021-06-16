package io.sentry;

import org.jetbrains.annotations.NotNull;

interface SpanListener {
  /**
   * Called when observed span finishes.
   *
   * @param span the span that has finished.
   */
  void onSpanFinished(final @NotNull Span span);
}
