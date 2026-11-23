package io.sentry.android.core.hang;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import io.sentry.android.core.anr.AnrStackTrace;
import io.sentry.time.EpochClock;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** Samples the stack and the state of a thread on each tick of a hang interval. */
@ApiStatus.Internal
public final class ThreadSampleCollector implements HangDataCollector<AnrStackTrace> {

  private final @NotNull EpochClock epochClock;
  private final @NotNull Thread thread;

  public ThreadSampleCollector(final @NotNull EpochClock epochClock, final @NotNull Thread thread) {
    this.epochClock = epochClock;
    this.thread = thread;
  }

  @Override
  public @NotNull AnrStackTrace onSample(final @NotNull HangInterval interval) {
    final long epochMs = NANOSECONDS.toMillis(epochClock.now().epochNanos());
    return new AnrStackTrace(epochMs, thread.getStackTrace(), thread.getState());
  }

  @Override
  public void onRecordingStopped(
      final @NotNull List<AnrStackTrace> samples, final @NotNull HangRecord.Builder out) {
    out.setSamples(new ArrayList<>(samples));
  }

  @Override
  public void close() {}
}
