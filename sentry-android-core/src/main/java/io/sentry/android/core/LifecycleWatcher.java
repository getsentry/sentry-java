package io.sentry.android.core;

import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import io.sentry.Breadcrumb;
import io.sentry.IHub;
import io.sentry.SentryLevel;
import io.sentry.Session;
import io.sentry.android.core.internal.util.BreadcrumbFactory;
import io.sentry.transport.CurrentDateProvider;
import io.sentry.transport.ICurrentDateProvider;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicLong;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

final class LifecycleWatcher implements DefaultLifecycleObserver {

  private final AtomicLong lastUpdatedSession = new AtomicLong(0L);

  private final long sessionIntervalMillis;

  private @Nullable TimerTask timerTask;
  private final @Nullable Timer timer;
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
    if (enableSessionTracking) {
      timer = new Timer(true);
    } else {
      timer = null;
    }
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
    if (enableSessionTracking) {
      cancelTask();

      final long currentTimeMillis = currentDateProvider.getCurrentTimeMillis();

      hub.withScope(
          scope -> {
            long lastUpdatedSession = this.lastUpdatedSession.get();
            if (lastUpdatedSession == 0L) {
              @Nullable Session currentSession = scope.getSession();
              if (currentSession != null && currentSession.getStarted() != null) {
                lastUpdatedSession = currentSession.getStarted().getTime();
              }
            }

            if (lastUpdatedSession == 0L
                || (lastUpdatedSession + sessionIntervalMillis) <= currentTimeMillis) {
              addSessionBreadcrumb("start");
              hub.startSession();
            }
            this.lastUpdatedSession.set(currentTimeMillis);
          });

      hub.withScope(
          scope -> {
            @Nullable final Session session = scope.getSession();
            if (session != null) {
              session.setAppInForeground(true);
            }
          });
    }
  }

  // App went to background and triggered this callback after 700ms
  // as no new screen was shown
  @Override
  public void onStop(final @NotNull LifecycleOwner owner) {
    if (enableSessionTracking) {
      final long currentTimeMillis = currentDateProvider.getCurrentTimeMillis();
      this.lastUpdatedSession.set(currentTimeMillis);

      scheduleEndSession();
    }

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
                addSessionBreadcrumb("end");
                hub.endSession();
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

  private void addSessionBreadcrumb(final @NotNull String state) {
    final Breadcrumb breadcrumb = BreadcrumbFactory.forSession(state);
    hub.addBreadcrumb(breadcrumb);
  }

  @TestOnly
  @Nullable
  TimerTask getTimerTask() {
    return timerTask;
  }

  @TestOnly
  @Nullable
  Timer getTimer() {
    return timer;
  }
}
