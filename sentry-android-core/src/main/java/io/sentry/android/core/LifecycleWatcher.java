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
   * <p>One deadline decides both halves of the background window: when the scheduled task ends the
   * session, and whether a foreground arriving first is soon enough to keep it. Measuring that one
   * window in two places used to mean two clock readings, which a clock step could make disagree.
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
   * Whether the session already bound to the scope is old enough that foregrounding should rotate
   * it.
   *
   * <p>Only reached before the app has been backgrounded in this process — a session started during
   * SDK init, most often. There is no tick to compare against at that point: the only record of
   * when that session started is {@link Session#getStarted()}, which is a wall-clock instant
   * because it is serialized and sent, so this comparison has to be a wall-clock one and inherits
   * the clock steps that come with it.
   *
   * <p>TODO [MAJOR]: have a session record the tick it started on, so this can be a {@link
   * Deadline} like the background window is. That tick would have to stay out of the payload.
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
