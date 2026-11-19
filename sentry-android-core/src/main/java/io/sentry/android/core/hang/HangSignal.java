package io.sentry.android.core.hang;

import java.io.Closeable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * A source that reports whether a watched thread is {@link State#SUSPICIOUS} or {@link State#IDLE}.
 */
@ApiStatus.Internal
public interface HangSignal extends Closeable {

  /**
   * Starts reporting to the listener, until {@link #stop()}. The signal may call the listener on
   * any thread.
   */
  void start(@NotNull HangSignalListener listener);

  /**
   * Stops reporting. After this, the signal must not call its listener until the next {@link
   * #start(HangSignalListener)}.
   */
  void stop();

  /** Called once, when the signal is no longer needed. The signal is not used after this. */
  @Override
  void close();

  enum State {
    IDLE,
    SUSPICIOUS
  }
}
