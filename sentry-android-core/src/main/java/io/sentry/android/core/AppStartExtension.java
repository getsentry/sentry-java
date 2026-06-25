package io.sentry.android.core;

import io.sentry.IAppStartExtender;
import io.sentry.ISentryLifecycleToken;
import io.sentry.ISpan;
import io.sentry.ITransaction;
import io.sentry.NoOpSpan;
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
  private @Nullable ISpan extendedSpan;
  private @Nullable ITransaction extendedTransaction;

  public AppStartExtension(final @NotNull AppStartMetrics metrics) {
    this.metrics = metrics;
  }

  public void setExtendAppStartListener(final @Nullable ExtendAppStartListener listener) {
    this.extendAppStartListener = listener;
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
      // Ignore the foreground check: headless app starts (broadcast/service) run in a
      // non-foreground process but can still be extended. The window gate still rejects an
      // extension once an activity was created, the first frame was drawn, or measurements were
      // already sent.
      if (!metrics.isAppStartWindowOpen()) {
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

  public boolean isActive() {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      return extendedTransaction != null && !extendedTransaction.isFinished();
    }
  }

  public void finishTransaction(final @NotNull SentryDate endTimestamp) {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      final @Nullable ITransaction transaction = extendedTransaction;
      if (transaction != null && !transaction.isFinished()) {
        transaction.finish(SpanStatus.OK, endTimestamp);
      }
    }
  }

  public @Nullable SentryDate getExtendedEndTime() {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      final @Nullable ISpan span = extendedSpan;
      if (span == null || !span.isFinished()) {
        return null;
      }
      // A deadline timeout would report an artificially inflated duration; suppress the vital
      // instead.
      if (span.getStatus() == SpanStatus.DEADLINE_EXCEEDED) {
        return null;
      }
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
