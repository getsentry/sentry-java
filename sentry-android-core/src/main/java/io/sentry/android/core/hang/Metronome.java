package io.sentry.android.core.hang;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** Calls its listeners at a regular interval with the current time. */
@ApiStatus.Internal
public interface Metronome {

  void addTickListener(@NotNull TickListener listener);

  void removeTickListener(@NotNull TickListener listener);

  interface TickListener {
    /**
     * Must be fast, it delays the metronome and the other listeners.
     *
     * @param nowMs a monotonic time in milliseconds, with an arbitrary origin
     */
    void onMetronomeTick(long nowMs);
  }
}
