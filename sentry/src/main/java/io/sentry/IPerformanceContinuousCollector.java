package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Used for collecting continuous data about vitals (slow, frozen frames, etc.) when a
 * transaction/span is running.
 */
@ApiStatus.Internal
public interface IPerformanceContinuousCollector extends IPerformanceCollector {

  /**
   * Called when a span is started.
   *
   * @param span the span that was started
   */
  void onSpanStarted(@NotNull ISpan span);

  /**
   * Called when a span itself is finished or a timeout is reached.
   *
   * @param span the span that was finished
   */
  void onSpanFinished(@NotNull ISpan span);

  /**
   * Called when no more data should be collected. Usually called when no more transactions/spans
   * are running or when the SDK is being closed.
   */
  void clear();
}
