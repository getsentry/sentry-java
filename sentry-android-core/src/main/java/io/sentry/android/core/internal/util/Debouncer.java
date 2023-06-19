package io.sentry.android.core.internal.util;

import io.sentry.transport.ICurrentDateProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** A simple time-based debouncing mechanism */
@ApiStatus.Internal
public class Debouncer {

  private final long waitTimeMs;
  private final @NotNull ICurrentDateProvider timeProvider;

  private Long lastExecutionTime = null;

  public Debouncer(final @NotNull ICurrentDateProvider timeProvider, final long waitTimeMs) {
    this.timeProvider = timeProvider;
    this.waitTimeMs = waitTimeMs;
  }

  /**
   * @return true if the execution should be debounced due to the last execution being within within
   *     waitTimeMs, otherwise false.
   */
  public boolean checkForDebounce() {
    final long now = timeProvider.getCurrentTimeMillis();
    if (lastExecutionTime == null || (lastExecutionTime + waitTimeMs) <= now) {
      lastExecutionTime = now;
      return false;
    }
    return true;
  }
}
