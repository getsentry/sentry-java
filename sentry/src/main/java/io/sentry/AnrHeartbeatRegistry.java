package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Internal entry point used by hybrid SDKs (e.g. Sentry for Unity, Unreal) to signal that the
 * monitored thread is alive in heartbeat-mode ANR detection. The platform-specific watchdog lives
 * in {@code sentry-android-core}, which the core {@code sentry} module cannot depend on — so the
 * watchdog registers a {@link Runnable} listener here at integration init time, and callers
 * dispatch through {@link #notifyAlive()}.
 *
 * <p>This class is internal SDK plumbing and not part of the public API. End-user app code should
 * not call it directly.
 */
@ApiStatus.Internal
public final class AnrHeartbeatRegistry {

  private AnrHeartbeatRegistry() {}

  private static volatile @Nullable Runnable listener;

  /**
   * Registers a heartbeat listener. Pass {@code null} to clear (e.g. on integration close).
   *
   * @param r the listener, or {@code null} to clear
   */
  public static void setListener(final @Nullable Runnable r) {
    listener = r;
  }

  /** Notifies the registered listener, if any. A no-op if no listener has been registered. */
  public static void notifyAlive() {
    final Runnable r = listener;
    if (r != null) {
      r.run();
    }
  }
}
