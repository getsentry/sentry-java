package io.sentry.android.core.hang;

import io.sentry.IDebugManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reports {@link HangSignal.State#SUSPICIOUS} when a probe posted to the watched thread did not run
 * for more than the suspicious threshold, and {@link HangSignal.State#IDLE} when it ran. The
 * watched thread is the thread that the {@link ThreadPoster} posts to.
 *
 * <p>Only the probe runs on the watched thread. Everything else runs on the thread that calls the
 * ticks of the {@link Metronome}. All times come from {@link Metronome#nowMs()}.
 *
 * <p>A hang starts at the last run of the probe. The watched thread can block at any time up to one
 * tick after that run, so the duration of a hang can be up to one polling interval too long.
 */
@ApiStatus.Internal
public final class ThreadHangSignal implements HangSignal, Metronome.TickListener {

  private final @NotNull Metronome metronome;
  private final @NotNull IDebugManager debugManager;
  private final @NotNull ThreadPoster poster;
  private final long suspiciousThresholdMs;

  // The origin of Metronome.nowMs() is arbitrary, so 0 cannot mean "never ran".
  private volatile long probeRunMs = Long.MIN_VALUE;

  private final @NotNull Runnable probe;

  private @Nullable HangSignalListener listener;
  private long probeFloorMs;
  private boolean suspicious;
  private long suspiciousSinceMs;

  public ThreadHangSignal(
      final @NotNull Metronome metronome,
      final @NotNull IDebugManager debugManager,
      final @NotNull ThreadPoster poster,
      final long suspiciousThresholdMs) {
    this.metronome = metronome;
    this.debugManager = debugManager;
    this.poster = poster;
    this.suspiciousThresholdMs = suspiciousThresholdMs;
    this.probe = () -> probeRunMs = metronome.nowMs();
  }

  @Override
  public void start(final @NotNull HangSignalListener listener) {
    this.listener = listener;
    suspicious = false;
    // The probe may not have run since the last stop, so its time would read as a hang.
    probeFloorMs = metronome.nowMs();
    metronome.addTickListener(this);
  }

  @Override
  public void stop() {
    metronome.removeTickListener(this);
    poster.remove(probe);
    listener = null;
    suspicious = false;
  }

  @Override
  public void close() {
    stop();
  }

  @Override
  public void onMetronomeTick(final long nowMs) {
    check(nowMs);
    poster.remove(probe);
    poster.post(probe);
  }

  private void check(final long nowMs) {
    final @Nullable HangSignalListener currentListener = listener;
    if (currentListener == null) {
      return;
    }

    // A breakpoint is not a hang.
    if (debugManager.isDebuggerConnected()) {
      probeFloorMs = nowMs;
      if (suspicious) {
        suspicious = false;
        currentListener.onSignalCanceled(this);
      }
      return;
    }

    final long lastProbeMs = Math.max(probeRunMs, probeFloorMs);
    if (suspicious) {
      if (lastProbeMs > suspiciousSinceMs) {
        suspicious = false;
        currentListener.onSignalChanged(this, State.IDLE, nowMs - lastProbeMs);
      }
    } else if (nowMs - lastProbeMs > suspiciousThresholdMs) {
      suspicious = true;
      suspiciousSinceMs = lastProbeMs;
      currentListener.onSignalChanged(this, State.SUSPICIOUS, nowMs - lastProbeMs);
    }
  }
}
