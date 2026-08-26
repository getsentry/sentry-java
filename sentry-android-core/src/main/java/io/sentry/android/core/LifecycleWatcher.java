package io.sentry.android.core;

import io.sentry.Breadcrumb;
import io.sentry.IScopes;
import io.sentry.ISentryLifecycleToken;
import io.sentry.SentryLevel;
import io.sentry.Session;
import io.sentry.transport.CurrentDateProvider;
import io.sentry.transport.ICurrentDateProvider;
import io.sentry.util.AutoClosableReentrantLock;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

final class LifecycleWatcher implements AppState.AppStateListener {

  private final AtomicLong lastUpdatedSession = new AtomicLong(0L);

  private final long sessionIntervalMillis;

  private @Nullable Future<?> endSessionFuture;
  private final @NotNull AutoClosableReentrantLock endSessionLock = new AutoClosableReentrantLock();
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

  @Override
  public void onForeground() {
    startSession();
    addAppBreadcrumb("foreground");
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
    }
    scopes.getOptions().getReplayController().resume();
    this.lastUpdatedSession.set(currentTimeMillis);
  }

  // App went to background and triggered this callback after 700ms
  // as no new screen was shown
  @Override
  public void onBackground() {
    final long currentTimeMillis = currentDateProvider.getCurrentTimeMillis();
    this.lastUpdatedSession.set(currentTimeMillis);

    scopes.getOptions().getReplayController().pause();
    scheduleEndSession();

    addAppBreadcrumb("background");
  }

  private void scheduleEndSession() {
    try (final @NotNull ISentryLifecycleToken ignored = endSessionLock.acquire()) {
      cancelTask();
      final @NotNull Runnable endSession =
          () -> {
            if (enableSessionTracking) {
              scopes.endSession();
            }
            scopes.getOptions().getReplayController().stop();
            scopes.getOptions().getContinuousProfiler().close(false);
          };

      try {
        endSessionFuture =
            scopes
                .getOptions()
                .getTimerExecutorService()
                .schedule(endSession, sessionIntervalMillis);
      } catch (Throwable e) {
        scopes
            .getOptions()
            .getLogger()
            .log(SentryLevel.WARNING, "Failed to schedule end of session. Ending it now.", e);
        // if we cannot re-check after the session interval, end the session right away instead of
        // leaving it open forever
        endSession.run();
      }
    }
  }

  private void cancelTask() {
    try (final @NotNull ISentryLifecycleToken ignored = endSessionLock.acquire()) {
      if (endSessionFuture != null) {
        endSessionFuture.cancel(false);
        endSessionFuture = null;
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
  Future<?> getEndSessionFuture() {
    return endSessionFuture;
  }
}
