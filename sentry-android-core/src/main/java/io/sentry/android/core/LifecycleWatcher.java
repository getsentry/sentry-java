package io.sentry.android.core;

import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import io.sentry.core.Breadcrumb;
import io.sentry.core.IHub;
import io.sentry.core.SentryLevel;
import io.sentry.core.transport.CurrentDateProvider;
import io.sentry.core.transport.ICurrentDateProvider;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

final class LifecycleWatcher implements DefaultLifecycleObserver {

  private long lastStartedSession = 0L;

  private final long sessionIntervalMillis;

  private @Nullable TimerTask timerTask;
  private final @NotNull Timer timer = new Timer(true);
  private final @NotNull IHub hub;
  private final boolean enableSessionTracking;
  private final boolean enableAppLifecycleBreadcrumbs;
  private final @NotNull AtomicBoolean runningSession = new AtomicBoolean();

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
  }

  private void startSession() {
    if (enableSessionTracking) {
      final long currentTimeMillis = currentDateProvider.getCurrentTimeMillis();
      cancelTask();
      if (lastStartedSession == 0L
          || (lastStartedSession + sessionIntervalMillis) <= currentTimeMillis) {
        addSessionBreadcrumb("start");
        hub.startSession();
        runningSession.set(true);
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
            runningSession.set(false);
          }
        };

    timer.schedule(timerTask, sessionIntervalMillis);
  }

  private void cancelTask() {
    if (timerTask != null) {
      timerTask.cancel();
      timerTask = null;
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

  @TestOnly
  @NotNull
  AtomicBoolean isRunningSession() {
    return runningSession;
  }

  @TestOnly
  @Nullable
  TimerTask getTimerTask() {
    return timerTask;
  }
}
