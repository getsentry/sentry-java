package io.sentry.android.core.hang;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import io.sentry.android.core.anr.AnrStackTrace;
import io.sentry.time.EpochClock;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** Samples the stack and the state of a thread on each sampling tick of a recorded hang. */
@ApiStatus.Internal
public final class ThreadSampleCollector implements HangDataCollector<AnrStackTrace> {

  private final @NotNull EpochClock epochClock;
  private final @NotNull Thread thread;

  public ThreadSampleCollector(final @NotNull EpochClock epochClock, final @NotNull Thread thread) {
    this.epochClock = epochClock;
    this.thread = thread;
  }

  @Override
  public @NotNull AnrStackTrace sample() {
    final long epochMs = NANOSECONDS.toMillis(epochClock.now().epochNanos());
    return new AnrStackTrace(epochMs, thread.getStackTrace(), thread.getState());
  }
}
