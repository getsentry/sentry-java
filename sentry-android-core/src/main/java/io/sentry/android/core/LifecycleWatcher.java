package io.sentry.android.core;

import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import io.sentry.Breadcrumb;
import io.sentry.IScopes;
import io.sentry.ISentryLifecycleToken;
import io.sentry.SentryLevel;
import io.sentry.Session;
import io.sentry.transport.CurrentDateProvider;
import io.sentry.transport.ICurrentDateProvider;
import io.sentry.util.AutoClosableReentrantLock;
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
  private final @NotNull AutoClosableReentrantLock timerLock = new AutoClosableReentrantLock();
  private final @NotNull IScopes scopes;
  private final boolean enableSessionTracking;
  private final boolean enableAppLifecycleBreadcrumbs;

  private final @NotNull ICurrentDateProvider currentDateProvider;

  LifecycleWatcher(
      final @NotNull IScopes scopes,
      final long sessionIntervalMillis,
      final boolean enableSessionTracking,
      final boolean enableAppLifecycleBreadcrumbs) {
    this(
        scopes,
        sessionIntervalMillis,
        enableSessionTracking,
        enableAppLifecycleBreadcrumbs,
        CurrentDateProvider.getInstance());
  }

  LifecycleWatcher(
      final @NotNull IScopes scopes,
      final long sessionIntervalMillis,
      final boolean enableSessionTracking,
      final boolean enableAppLifecycleBreadcrumbs,
      final @NotNull ICurrentDateProvider currentDateProvider) {
    this.sessionIntervalMillis = sessionIntervalMillis;
    this.enableSessionTracking = enableSessionTracking;
    this.enableAppLifecycleBreadcrumbs = enableAppLifecycleBreadcrumbs;
    this.scopes = scopes;
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

    scopes.configureScope(
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
        scopes.startSession();
      }
      scopes.getOptions().getReplayController().start();
    } else if (!isFreshSession.get()) {
      // only resume if it's not a fresh session, which has been started in SentryAndroid.init
      scopes.getOptions().getReplayController().resume();
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

    scopes.getOptions().getReplayController().pause();
    scheduleEndSession();

    AppState.getInstance().setInBackground(true);
    addAppBreadcrumb("background");
  }

  private void scheduleEndSession() {
    try (final @NotNull ISentryLifecycleToken ignored = timerLock.acquire()) {
      cancelTask();
      if (timer != null) {
        timerTask =
            new TimerTask() {
              @Override
              public void run() {
                if (enableSessionTracking) {
                  scopes.endSession();
                }
                scopes.getOptions().getReplayController().stop();
              }
            };

        timer.schedule(timerTask, sessionIntervalMillis);
      }
    }
  }

  private void cancelTask() {
    try (final @NotNull ISentryLifecycleToken ignored = timerLock.acquire()) {
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
      scopes.addBreadcrumb(breadcrumb);
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
