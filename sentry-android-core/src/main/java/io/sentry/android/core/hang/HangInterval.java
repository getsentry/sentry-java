package io.sentry.android.core.hang;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class HangInterval {

  private final long startTimeMs;

  public HangInterval(final long startTimeMs) {
    this.startTimeMs = startTimeMs;
  }

  /**
   * The start of the hang, in the time base of {@link
   * Metronome.TickListener#onMetronomeTick(long)}.
   */
  public long getStartTimeMs() {
    return startTimeMs;
  }
}
