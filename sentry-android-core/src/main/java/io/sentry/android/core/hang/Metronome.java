package io.sentry.android.core.hang;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** Calls its listeners at a regular interval with the current time. */
@ApiStatus.Internal
public interface Metronome {

  void addTickListener(@NotNull TickListener listener);

  void removeTickListener(@NotNull TickListener listener);

  /**
   * The current time of this metronome, as a monotonic time in milliseconds with an arbitrary
   * origin. It has the same origin as the time of each tick. Can be called from any thread.
   */
  long nowMs();

  interface TickListener {
    /**
     * Must be fast, it delays the metronome and the other listeners.
     *
     * @param nowMs the time of this tick, see {@link Metronome#nowMs()}
     */
    void onMetronomeTick(long nowMs);
  }
}
