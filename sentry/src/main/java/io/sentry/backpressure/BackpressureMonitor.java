package io.sentry.backpressure;

import io.sentry.IScopes;
import io.sentry.ISentryExecutorService;
import io.sentry.ISentryLifecycleToken;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.util.AutoClosableReentrantLock;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class BackpressureMonitor implements IBackpressureMonitor, Runnable {
  static final int MAX_DOWNSAMPLE_FACTOR = 10;
  private static final int INITIAL_CHECK_DELAY_IN_MS = 500;
  private static final int CHECK_INTERVAL_IN_MS = 10 * 1000;

  private final @NotNull SentryOptions sentryOptions;
  private final @NotNull IScopes scopes;
  private int downsampleFactor = 0;
  private volatile @Nullable Future<?> latestScheduledRun = null;
  private final @NotNull AutoClosableReentrantLock lock = new AutoClosableReentrantLock();

  public BackpressureMonitor(
      final @NotNull SentryOptions sentryOptions, final @NotNull IScopes scopes) {
    this.sentryOptions = sentryOptions;
    this.scopes = scopes;
  }

  @Override
  public void start() {
    reschedule(INITIAL_CHECK_DELAY_IN_MS);
  }

  @Override
  public void run() {
    checkHealth();
    reschedule(CHECK_INTERVAL_IN_MS);
  }

  @Override
  public int getDownsampleFactor() {
    return downsampleFactor;
  }

  @Override
  public void close() {
    final @Nullable Future<?> currentRun = latestScheduledRun;
    if (currentRun != null) {
      try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
        currentRun.cancel(true);
      }
    }
  }

  void checkHealth() {
    if (isHealthy()) {
      if (downsampleFactor > 0) {
        sentryOptions
            .getLogger()
            .log(SentryLevel.DEBUG, "Health check positive, reverting to normal sampling.");
      }
      downsampleFactor = 0;
    } else {
      if (downsampleFactor < MAX_DOWNSAMPLE_FACTOR) {
        downsampleFactor++;
        sentryOptions
            .getLogger()
            .log(
                SentryLevel.DEBUG,
                "Health check negative, downsampling with a factor of %d",
                downsampleFactor);
      }
    }
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  private void reschedule(final int delay) {
    final @NotNull ISentryExecutorService executorService = sentryOptions.getExecutorService();
    if (!executorService.isClosed()) {
      try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
        try {
          latestScheduledRun = executorService.schedule(this, delay);
        } catch (RejectedExecutionException e) {
          sentryOptions
              .getLogger()
              .log(SentryLevel.DEBUG, "Backpressure monitor reschedule task rejected", e);
        }
      }
    }
  }

  private boolean isHealthy() {
    return scopes.isHealthy();
  }
}
