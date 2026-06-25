package io.sentry.android.core;

import io.sentry.IAppStartExtender;
import io.sentry.ILogger;
import io.sentry.ISentryLifecycleToken;
import io.sentry.ISpan;
import io.sentry.ITransaction;
import io.sentry.NoOpLogger;
import io.sentry.NoOpSpan;
import io.sentry.SentryDate;
import io.sentry.SentryLevel;
import io.sentry.SpanStatus;
import io.sentry.android.core.performance.AppStartMetrics;
import io.sentry.util.AutoClosableReentrantLock;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Owns the lifecycle of an extended app start. Created and held by {@link AppStartMetrics}, it
 * keeps the new "extend app start" concern out of that already-large class.
 *
 * <p>Both the eager standalone App Start {@link ITransaction} and its extended child {@link ISpan}
 * are created by the integration (which has access to scopes) and handed back here via {@link
 * #onExtended(ITransaction, ISpan)}. This component owns them from then on: it never stores them in
 * the integration's shared transaction field, so the per-activity cleanup can never cancel an
 * eagerly-created extension.
 */
@ApiStatus.Internal
public final class AppStartExtension implements IAppStartExtender {

  /**
   * Notifies the integration that an extension was requested. The integration creates the
   * standalone App Start transaction + extended child span (it has scopes) and hands them back via
   * {@link #onExtended(ITransaction, ISpan)}. When no listener is registered (e.g. standalone
   * tracing is disabled), {@link #extendAppStart()} is inert and the whole API stays a no-op.
   */
  public interface ExtendAppStartListener {
    void onExtendAppStartRequested();
  }

  private final @NotNull AppStartMetrics metrics;
  private final @NotNull AutoClosableReentrantLock lock = new AutoClosableReentrantLock();

  // Set once at SDK init via setLogger(), read later when an extension is requested. Defaults to a
  // no-op because this component is created before SentryOptions (and its logger) exist.
  private volatile @NotNull ILogger logger = NoOpLogger.getInstance();

  private @Nullable ExtendAppStartListener extendAppStartListener;
  private @Nullable ISpan extendedSpan;
  private @Nullable ITransaction extendedTransaction;

  public AppStartExtension(final @NotNull AppStartMetrics metrics) {
    this.metrics = metrics;
  }

  public void setExtendAppStartListener(final @Nullable ExtendAppStartListener listener) {
    this.extendAppStartListener = listener;
  }

  void setLogger(final @NotNull ILogger logger) {
    this.logger = logger;
  }

  @Override
  public void extendAppStart() {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      if (extendedSpan != null) {
        logger.log(SentryLevel.WARNING, "App start is already being extended.");
        return;
      }
      // Ignore the foreground check: headless app starts (broadcast/service) run in a
      // non-foreground process but can still be extended. The window gate still rejects an
      // extension once an activity was created, the first frame was drawn, or measurements were
      // already sent.
      if (!metrics.isAppStartWindowOpen()) {
        logger.log(
            SentryLevel.WARNING,
            "Cannot extend app start: the app start window has already passed.");
        return;
      }
      final @Nullable ExtendAppStartListener listener = extendAppStartListener;
      if (listener != null) {
        listener.onExtendAppStartRequested();
      }
    }
  }

  /**
   * Hands the eagerly-created standalone App Start transaction and its extended child span over to
   * this component, which owns them from now on. Called synchronously by the integration while
   * handling {@link ExtendAppStartListener#onExtendAppStartRequested()}.
   */
  public void onExtended(
      final @NotNull ITransaction transaction, final @NotNull ISpan extendedSpan) {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      this.extendedTransaction = transaction;
      this.extendedSpan = extendedSpan;
    }
  }

  @Override
  public void finishAppStart() {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      final @Nullable ISpan span = extendedSpan;
      if (span != null && !span.isFinished()) {
        span.finish(SpanStatus.OK);
      }
    }
  }

  @Override
  public @NotNull ISpan getExtendedAppStartSpan() {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      final @Nullable ISpan span = extendedSpan;
      if (span != null && !span.isFinished()) {
        return span;
      }
      return NoOpSpan.getInstance();
    }
  }

  /** Whether an eagerly-created extension transaction exists and has not finished yet. */
  public boolean isActive() {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      return extendedTransaction != null && !extendedTransaction.isFinished();
    }
  }

  /**
   * Finishes the owned transaction at the natural app start end (first frame, or the headless stop
   * time). {@code waitForChildren} holds the transaction open until the extended span finishes, so
   * the app start vital is never captured before this point. Idempotent.
   */
  public void finishTransaction(final @NotNull SentryDate endTimestamp) {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      final @Nullable ITransaction transaction = extendedTransaction;
      if (transaction != null && !transaction.isFinished()) {
        transaction.finish(SpanStatus.OK, endTimestamp);
      }
    }
  }

  /**
   * The effective end of the extended app start, used to extend the app start vital. Returns {@code
   * null} when no extension finished, or when it finished via the deadline timeout - in the latter
   * case the vital is suppressed instead of reporting an artificially inflated duration.
   */
  public @Nullable SentryDate getExtendedEndTime() {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      final @Nullable ISpan span = extendedSpan;
      if (span == null || !span.isFinished()) {
        return null;
      }
      if (span.getStatus() == SpanStatus.DEADLINE_EXCEEDED) {
        return null;
      }
      return span.getFinishDate();
    }
  }

  /**
   * Resets the per-start state so a stale extension can't affect a later (e.g. warm) app start. The
   * registered listener is intentionally kept: it is registered once at SDK init and must survive
   * across app starts.
   */
  public void reset() {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      extendedSpan = null;
      extendedTransaction = null;
    }
  }
}
