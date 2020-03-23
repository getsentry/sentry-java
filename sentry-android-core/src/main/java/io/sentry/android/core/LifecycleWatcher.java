package io.sentry.android.core;

import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import io.sentry.core.IHub;
import java.util.Timer;
import java.util.TimerTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class LifecycleWatcher implements DefaultLifecycleObserver {

  private long lastStartedSession = 0L;

  private final long sessionIntervalMillis;

  private @Nullable TimerTask timerTask;
  private final @NotNull Timer timer = new Timer(true);
  private final @NotNull IHub hub;

  LifecycleWatcher(final @NotNull IHub hub, final long sessionIntervalMillis) {
    this.sessionIntervalMillis = sessionIntervalMillis;
    this.hub = hub;
  }

  // App goes to foreground
  @Override
  public void onStart(final @NotNull LifecycleOwner owner) {
    final long currentTimeMillis = System.currentTimeMillis();
    cancelTask();
    if (lastStartedSession == 0L
        || (lastStartedSession + sessionIntervalMillis) <= currentTimeMillis) {
      hub.startSession();
    }
    lastStartedSession = currentTimeMillis;
  }

  // App went to background and triggered this callback after 700ms
  // as no new screen was shown
  @Override
  public void onStop(final @NotNull LifecycleOwner owner) {
    scheduleEndSession();
  }

  private void scheduleEndSession() {
    cancelTask();
    timerTask =
        new TimerTask() {
          @Override
          public void run() {
            hub.endSession();
          }
        };

    timer.schedule(timerTask, sessionIntervalMillis);
  }

  private void cancelTask() {
    if (timerTask != null) {
      timerTask.cancel();
    }
  }
}
