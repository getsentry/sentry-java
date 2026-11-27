package io.sentry.android.core.hang;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** Receives the state changes of a {@link HangSignal}. */
@ApiStatus.Internal
public interface HangSignalListener {

  /**
   * The signal changed its state.
   *
   * @param durationMs how long before this call the watched thread entered the new state; a signal
   *     can detect a change late
   */
  void onSignalChanged(
      @NotNull HangSignal signal, @NotNull HangSignal.State state, long durationMs);

  /**
   * The current {@link HangSignal.State#SUSPICIOUS} state is not valid anymore and ended without a
   * recovery.
   */
  void onSignalCanceled(@NotNull HangSignal signal);
}
