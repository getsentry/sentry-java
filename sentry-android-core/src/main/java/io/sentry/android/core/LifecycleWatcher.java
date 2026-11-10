package io.sentry.android.core;

import io.sentry.Breadcrumb;
import io.sentry.IScopes;
import io.sentry.ISentryLifecycleToken;
import io.sentry.SentryLevel;
import io.sentry.Session;
import io.sentry.time.Deadline;
import io.sentry.time.EpochClock;
import io.sentry.time.MonotonicTicker;
import io.sentry.util.AutoClosableReentrantLock;
import java.util.Date;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

final class LifecycleWatcher implements AppState.AppStateListener {

  private final long sessionIntervalMillis;

  /**
   * When the session the app left behind stops being resumable, or null while in the foreground.
   *
   * <p>Only read or written while holding {@link #endSessionLock}, which is also what lets
   * cancelling the pending task and taking this deadline happen as one step.
   */
  private @Nullable Deadline sessionEnd;

  private @Nullable Future<?> endSessionFuture;
  private final @NotNull AutoClosableReentrantLock endSessionLock = new AutoClosableReentrantLock();
  private final @NotNull IScopes scopes;
  private final boolean enableSessionTracking;
  private final boolean enableAppLifecycleBreadcrumbs;

  private final @NotNull MonotonicTicker ticker;
  private final @NotNull EpochClock epochClock;

  LifecycleWatcher(
      final @NotNull IScopes scopes,
      final long sessionIntervalMillis,
      final boolean enableSessionTracking,
      final boolean enableAppLifecycleBreadcrumbs,
      final @NotNull MonotonicTicker ticker,
      final @NotNull EpochClock epochClock) {
    this.sessionIntervalMillis = sessionIntervalMillis;
    this.enableSessionTracking = enableSessionTracking;
    this.enableAppLifecycleBreadcrumbs = enableAppLifecycleBreadcrumbs;
    this.scopes = scopes;
    this.ticker = ticker;
    this.epochClock = epochClock;
  }

  @Override
  public void onForeground() {
    startSession();
    addAppBreadcrumb("foreground");
  }

  private void startSession() {
    final @Nullable Deadline sessionEnd = takeSessionEnd();

    final boolean startNewSession =
        sessionEnd != null ? sessionEnd.hasPassed() : isSessionOnScopeStale();
    if (startNewSession) {
      if (enableSessionTracking) {
        scopes.startSession();
      }
    }
    scopes.getOptions().getReplayController().onAppForegrounded(startNewSession);
  }

  /**
   * Whether the session on the scope is too old to resume, so foregrounding should start a new one.
   *
   * <p>Used when no background window is pending, which means the session was started by SDK init
   * rather than by leaving and returning to the app. Nothing captured a tick back then, and the
   * only record of when the session started is {@link Session#getStarted()} — a wall-clock instant,
   * because it is sent to Sentry. So this check stays on the wall clock, clock steps included.
   *
   * <p>TODO [MAJOR]: let a session remember the tick it started on, so this can use a {@link
   * Deadline} too. That tick must not be serialized.
   */
  private boolean isSessionOnScopeStale() {
    final long nowMillis = TimeUnit.NANOSECONDS.toMillis(epochClock.now().epochNanos());
    // No session, or one that never recorded a start, leaves nothing to resume.
    final @NotNull AtomicBoolean stale = new AtomicBoolean(true);
    scopes.configureScope(
        scope -> {
          final @Nullable Session session = scope.getSession();
          final @Nullable Date started = session == null ? null : session.getStarted();
          if (started != null) {
            stale.set(started.getTime() + sessionIntervalMillis <= nowMillis);
          }
        });
    return stale.get();
  }

  // App went to background and triggered this callback after 700ms
  // as no new screen was shown
  @Override
  public void onBackground() {
    scopes.getOptions().getReplayController().onAppBackgrounded();
    scheduleEndSession();

    addAppBreadcrumb("background");
  }

  private void scheduleEndSession() {
    try (final @NotNull ISentryLifecycleToken ignored = endSessionLock.acquire()) {
      cancelTask();
      final @NotNull Deadline sessionEnd =
          Deadline.after(ticker, sessionIntervalMillis, TimeUnit.MILLISECONDS);
      this.sessionEnd = sessionEnd;
      final @NotNull Runnable endSession =
          () -> {
            if (enableSessionTracking) {
              scopes.endSession();
            }
            scopes.getOptions().getReplayController().onAppSessionEnded();
            scopes.getOptions().getContinuousProfiler().close(false);
          };

      try {
        // The executor's own delay stops while the device is suspended, while the deadline keeps
        // counting, so this task can only run at or after the deadline. It needs no second check.
        endSessionFuture =
            scopes
                .getOptions()
                .getTimerExecutorService()
                .schedule(endSession, sessionEnd.remaining(TimeUnit.MILLISECONDS));
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

  /** Stops the pending end of session and hands back the deadline it was going to run at. */
  private @Nullable Deadline takeSessionEnd() {
    try (final @NotNull ISentryLifecycleToken ignored = endSessionLock.acquire()) {
      cancelTask();
      final @Nullable Deadline sessionEnd = this.sessionEnd;
      this.sessionEnd = null;
      return sessionEnd;
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
