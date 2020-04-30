package io.sentry.android.core;

import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import io.sentry.core.Breadcrumb;
import io.sentry.core.IHub;
import io.sentry.core.SentryLevel;
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
  private final boolean enableSessionTracking;
  private final boolean enableAppLifecycleBreadcrumbs;

  LifecycleWatcher(
      final @NotNull IHub hub,
      final long sessionIntervalMillis,
      final boolean enableSessionTracking,
      final boolean enableAppLifecycleBreadcrumbs) {
    this.sessionIntervalMillis = sessionIntervalMillis;
    this.enableSessionTracking = enableSessionTracking;
    this.enableAppLifecycleBreadcrumbs = enableAppLifecycleBreadcrumbs;
    this.hub = hub;
  }

  // App goes to foreground
  @Override
  public void onStart(final @NotNull LifecycleOwner owner) {
    startSession();
    addAppBreadcrumb("foreground");
  }

  private void startSession() {
    if (enableSessionTracking) {
      final long currentTimeMillis = System.currentTimeMillis();
      cancelTask();
      if (lastStartedSession == 0L
          || (lastStartedSession + sessionIntervalMillis) <= currentTimeMillis) {
        addSessionBreadcrumb("start");
        hub.startSession();
      }
      lastStartedSession = currentTimeMillis;
    }
  }

  // App went to background and triggered this callback after 700ms
  // as no new screen was shown
  @Override
  public void onStop(final @NotNull LifecycleOwner owner) {
    if (enableSessionTracking) {
      scheduleEndSession();
    }

    addAppBreadcrumb("background");
  }

  private void scheduleEndSession() {
    cancelTask();
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

  private void cancelTask() {
    if (timerTask != null) {
      timerTask.cancel();
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
    final Breadcrumb breadcrumb = new Breadcrumb();
    breadcrumb.setType("session");
    breadcrumb.setData("state", state);
    breadcrumb.setCategory("app.lifecycle");
    breadcrumb.setLevel(SentryLevel.INFO);
    hub.addBreadcrumb(breadcrumb);
  }
}
