package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface SpanFinishedCallback {
  /**
   * Called when observed span finishes.
   *
   * @param span the span that has finished.
   */
  void execute(final @NotNull Span span);
}
