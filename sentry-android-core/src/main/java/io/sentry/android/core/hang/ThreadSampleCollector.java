package io.sentry.android.core.hang;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import io.sentry.android.core.anr.AnrStackTrace;
import io.sentry.time.Timestamp;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** Samples the stack and the state of a thread on each sampling tick of a recorded hang. */
@ApiStatus.Internal
public final class ThreadSampleCollector implements HangDataCollector<AnrStackTrace> {

  private final @NotNull Thread thread;

  public ThreadSampleCollector(final @NotNull Thread thread) {
    this.thread = thread;
  }

  @Override
  public @NotNull AnrStackTrace sample(final @NotNull Timestamp timestamp) {
    return new AnrStackTrace(
        NANOSECONDS.toMillis(timestamp.epochNanos()), thread.getStackTrace(), thread.getState());
  }
}
