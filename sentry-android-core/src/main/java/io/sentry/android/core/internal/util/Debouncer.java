package io.sentry.android.core.internal.util;

import io.sentry.time.Deadline;
import io.sentry.time.MonotonicTicker;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * A simple time-based debouncing mechanism.
 *
 * <p>The window is measured on a {@link MonotonicTicker}, which on Android keeps counting through
 * deep sleep, so a window that expired while the device slept is treated as expired.
 */
@ApiStatus.Internal
public final class Debouncer {

  private final @NotNull MonotonicTicker ticker;
  private final long waitTimeMs;
  private final int maxExecutions;
  private final @NotNull AtomicInteger executions = new AtomicInteger(0);
  private volatile @NotNull Deadline windowEnd;

  public Debouncer(
      final @NotNull MonotonicTicker ticker, final long waitTimeMs, final int maxExecutions) {
    this.ticker = ticker;
    this.waitTimeMs = waitTimeMs;
    this.maxExecutions = maxExecutions <= 0 ? 1 : maxExecutions;
    this.windowEnd = Deadline.passed(ticker);
  }

  /**
   * Returns true if the execution should be debounced due to maxExecutions executions being made
   * within waitTimeMs, otherwise false.
   */
  public boolean checkForDebounce() {
    if (windowEnd.hasPassed()) {
      executions.set(0);
      windowEnd = Deadline.after(ticker, waitTimeMs, TimeUnit.MILLISECONDS);
      return false;
    }
    if (executions.incrementAndGet() < maxExecutions) {
      return false;
    }
    executions.set(0);
    return true;
  }
}
