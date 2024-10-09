package io.sentry.android.core;

import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import io.sentry.Breadcrumb;
import io.sentry.IHub;
import io.sentry.SentryLevel;
import io.sentry.Session;
import io.sentry.transport.CurrentDateProvider;
import io.sentry.transport.ICurrentDateProvider;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

final class LifecycleWatcher implements DefaultLifecycleObserver {

  private final AtomicLong lastUpdatedSession = new AtomicLong(0L);
  private final AtomicBoolean isFreshSession = new AtomicBoolean(false);

  private final long sessionIntervalMillis;

  private @Nullable TimerTask timerTask;
  private final @NotNull Timer timer = new Timer(true);
  private final @NotNull Object timerLock = new Object();
  private final @NotNull IHub hub;
  private final boolean enableSessionTracking;
  private final boolean enableAppLifecycleBreadcrumbs;

  private final @NotNull ICurrentDateProvider currentDateProvider;

  LifecycleWatcher(
      final @NotNull IHub hub,
      final long sessionIntervalMillis,
      final boolean enableSessionTracking,
      final boolean enableAppLifecycleBreadcrumbs) {
    this(
        hub,
        sessionIntervalMillis,
        enableSessionTracking,
        enableAppLifecycleBreadcrumbs,
        CurrentDateProvider.getInstance());
  }

  LifecycleWatcher(
      final @NotNull IHub hub,
      final long sessionIntervalMillis,
      final boolean enableSessionTracking,
      final boolean enableAppLifecycleBreadcrumbs,
      final @NotNull ICurrentDateProvider currentDateProvider) {
    this.sessionIntervalMillis = sessionIntervalMillis;
    this.enableSessionTracking = enableSessionTracking;
    this.enableAppLifecycleBreadcrumbs = enableAppLifecycleBreadcrumbs;
    this.hub = hub;
    this.currentDateProvider = currentDateProvider;
  }

  // App goes to foreground
  @Override
  public void onStart(final @NotNull LifecycleOwner owner) {
    startSession();
    addAppBreadcrumb("foreground");

    // Consider using owner.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED);
    // in the future.
    AppState.getInstance().setInBackground(false);
  }

  private void startSession() {
    cancelTask();

    final long currentTimeMillis = currentDateProvider.getCurrentTimeMillis();

    hub.configureScope(
        scope -> {
          if (lastUpdatedSession.get() == 0L) {
            final @Nullable Session currentSession = scope.getSession();
            if (currentSession != null && currentSession.getStarted() != null) {
              lastUpdatedSession.set(currentSession.getStarted().getTime());
              isFreshSession.set(true);
            }
          }
        });

    final long lastUpdatedSession = this.lastUpdatedSession.get();
    if (lastUpdatedSession == 0L
        || (lastUpdatedSession + sessionIntervalMillis) <= currentTimeMillis) {
      if (enableSessionTracking) {
        hub.startSession();
      }
      hub.getOptions().getReplayController().start();
    } else if (!isFreshSession.get()) {
      // only resume if it's not a fresh session, which has been started in SentryAndroid.init
      hub.getOptions().getReplayController().resume();
    }
    isFreshSession.set(false);
    this.lastUpdatedSession.set(currentTimeMillis);
  }

  // App went to background and triggered this callback after 700ms
  // as no new screen was shown
  @Override
  public void onStop(final @NotNull LifecycleOwner owner) {
    final long currentTimeMillis = currentDateProvider.getCurrentTimeMillis();
    this.lastUpdatedSession.set(currentTimeMillis);

    hub.getOptions().getReplayController().pause();
    scheduleEndSession();

    AppState.getInstance().setInBackground(true);
    addAppBreadcrumb("background");
  }

  private void scheduleEndSession() {
    synchronized (timerLock) {
      cancelTask();
      if (timer != null) {
        timerTask =
            new TimerTask() {
              @Override
              public void run() {
                if (enableSessionTracking) {
                  hub.endSession();
                }
                hub.getOptions().getReplayController().stop();
              }
            };

        timer.schedule(timerTask, sessionIntervalMillis);
      }
    }
  }

  private void cancelTask() {
    synchronized (timerLock) {
      if (timerTask != null) {
        timerTask.cancel();
        timerTask = null;
      }
    }
  }

  private void addAppBreadcrumb(final @NotNull String state) {
    if (enableAppLifecycleBreadcrumbs) {
      final Breadcrumb breadcrumb = new Breadcrumb();
      breadcrumb.setType("navigation");
      breadcrumb.setData("state", state);
      breadcrumb.setCategory("app.lifecycle");
      breadcrumb.setLevel(SentryLevel.INFO);
      hub.addBreadcrumb(breadcrumb);
    }
  }

  @TestOnly
  @Nullable
  TimerTask getTimerTask() {
    return timerTask;
  }

  @TestOnly
  @NotNull
  Timer getTimer() {
    return timer;
  }
}
