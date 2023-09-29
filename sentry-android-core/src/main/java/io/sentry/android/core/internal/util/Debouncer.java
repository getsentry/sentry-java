package io.sentry.android.core.internal.util;

import io.sentry.transport.ICurrentDateProvider;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** A simple time-based debouncing mechanism */
@ApiStatus.Internal
public class Debouncer {

  private final long waitTimeMs;
  private final @NotNull ICurrentDateProvider timeProvider;
  private final @NotNull AtomicInteger executions = new AtomicInteger(0);
  private final int maxExecutions;

  private final @NotNull AtomicLong lastExecutionTime = new AtomicLong(0);

  public Debouncer(
      final @NotNull ICurrentDateProvider timeProvider,
      final long waitTimeMs,
      final int maxExecutions) {
    this.timeProvider = timeProvider;
    this.waitTimeMs = waitTimeMs;
    this.maxExecutions = maxExecutions <= 0 ? 1 : maxExecutions;
  }

  /**
   * @return true if the execution should be debounced due to the last execution being within within
   *     waitTimeMs, otherwise false.
   */
  public boolean checkForDebounce() {
    final long now = timeProvider.getCurrentTimeMillis();
    if ((lastExecutionTime.get() + waitTimeMs) <= now) {
      executions.set(0);
      lastExecutionTime.set(now);
      return false;
    }
    if (executions.incrementAndGet() < maxExecutions) {
      return false;
    }
    executions.set(0);
    return true;
  }
}
