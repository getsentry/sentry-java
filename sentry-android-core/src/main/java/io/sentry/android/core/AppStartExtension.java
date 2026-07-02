package io.sentry.android.core;

import io.sentry.IAppStartExtender;
import io.sentry.ISentryLifecycleToken;
import io.sentry.ISpan;
import io.sentry.ITransaction;
import io.sentry.Sentry;
import io.sentry.SentryDate;
import io.sentry.SentryLevel;
import io.sentry.SpanStatus;
import io.sentry.android.core.performance.AppStartMetrics;
import io.sentry.util.AutoClosableReentrantLock;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class AppStartExtension implements IAppStartExtender {

  public static final class ExtendedAppStart {
    public final @NotNull ITransaction transaction;
    public final @NotNull ISpan span;

    public ExtendedAppStart(final @NotNull ITransaction transaction, final @NotNull ISpan span) {
      this.transaction = transaction;
      this.span = span;
    }
  }

  public interface ExtendAppStartListener {
    @Nullable
    ExtendedAppStart onExtendAppStartRequested();
  }

  private final @NotNull AppStartMetrics metrics;
  private final @NotNull AutoClosableReentrantLock lock = new AutoClosableReentrantLock();

  private @Nullable ExtendAppStartListener extendAppStartListener;
  // We hold onto both the span and its transaction because they mean different things and finish
  // at different times:
  //
  //  - extendedSpan is what the app developer works with: they get it from
  //    getExtendedAppStartSpan(), add their own child spans to it, and finish it by calling
  //    finishExtendedAppStart(). Its end time is what extends the app start measurement.
  //
  //  - extendedTransaction is the standalone "app.start" transaction that actually gets sent to
  //    Sentry. It carries the span and the screen name. The SDK asks it to finish at the first
  //    frame (or headless end), but because it uses waitForChildren it stays open until the span
  //    finishes (or the deadline is hit).
  //
  // A span doesn't expose its transaction, and pulling the span back out of the transaction would
  // be fragile, so we just keep a reference to each.
  private @Nullable ISpan extendedSpan;
  private @Nullable ITransaction extendedTransaction;

  public AppStartExtension(final @NotNull AppStartMetrics metrics) {
    this.metrics = metrics;
  }

  public void setExtendAppStartListener(final @Nullable ExtendAppStartListener listener) {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      this.extendAppStartListener = listener;
    }
  }

  @Override
  public void extendAppStart() {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      if (extendedSpan != null) {
        Sentry.getCurrentScopes()
            .getOptions()
            .getLogger()
            .log(SentryLevel.WARNING, "App start is already being extended.");
        return;
      }
      if (!metrics.canExtendAppStart()) {
        Sentry.getCurrentScopes()
            .getOptions()
            .getLogger()
            .log(
                SentryLevel.WARNING,
                "Cannot extend app start: the app start window has already passed.");
        return;
      }
      final @Nullable ExtendAppStartListener listener = extendAppStartListener;
      if (listener != null) {
        final @Nullable ExtendedAppStart extended = listener.onExtendAppStartRequested();
        if (extended != null) {
          this.extendedTransaction = extended.transaction;
          this.extendedSpan = extended.span;
        }
      }
    }
  }

  /**
   * Sets data on the owned (eager) transaction if it is still open. Used to attach the screen name
   * once the first activity is known, since the transaction is created in {@code onCreate} before
   * any activity exists.
   */
  public void setData(final @NotNull String key, final @Nullable Object value) {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      if (extendedTransaction != null && !extendedTransaction.isFinished()) {
        extendedTransaction.setData(key, value);
      }
    }
  }

  @Override
  public void finishExtendedAppStart() {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      final @Nullable ISpan span = extendedSpan;
      if (span != null && !span.isFinished()) {
        span.finish(SpanStatus.OK);
      }
    }
  }

  @Override
  public @Nullable ISpan getExtendedAppStartSpan() {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      final @Nullable ISpan span = extendedSpan;
      // Mirrors getExtendedEndTime(): the finish date is set before isFinished() flips.
      if (span != null && span.getFinishDate() == null) {
        return span;
      }
      return null;
    }
  }

  public boolean isActive() {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      return extendedTransaction != null && !extendedTransaction.isFinished();
    }
  }

  /**
   * Whether this app start was extended at all, regardless of finish or deadline state. Used by the
   * event processor to decide whether to apply the never-shorten vital logic.
   */
  public boolean isExtended() {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      return extendedSpan != null;
    }
  }

  public void finishTransaction(final @NotNull SentryDate endTimestamp) {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      final @Nullable ITransaction transaction = extendedTransaction;
      if (transaction != null && !transaction.isFinished()) {
        final @Nullable ISpan span = extendedSpan;
        final @Nullable SentryDate spanEnd = span == null ? null : span.getFinishDate();
        final @NotNull SentryDate end =
            spanEnd != null && spanEnd.isAfter(endTimestamp) ? spanEnd : endTimestamp;
        transaction.finish(SpanStatus.OK, end);
      }
    }
  }

  public @Nullable SentryDate getExtendedEndTime() {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      final @Nullable ISpan span = extendedSpan;
      if (span == null) {
        return null;
      }
      // A deadline timeout would report an artificially inflated duration; suppress the vital
      // instead.
      if (span.getStatus() == SpanStatus.DEADLINE_EXCEEDED) {
        return null;
      }
      // Read the finish date, not isFinished(): finishing the extended span completes the
      // waitForChildren transaction and runs the event processor re-entrantly before the span's
      // finished flag is set, but the finish timestamp is already in place. Null until finished.
      return span.getFinishDate();
    }
  }

  public void clear() {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      extendedSpan = null;
      extendedTransaction = null;
    }
  }
}
