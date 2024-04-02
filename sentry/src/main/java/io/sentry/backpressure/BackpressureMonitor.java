package io.sentry.backpressure;

import io.sentry.IScopes;
import io.sentry.ISentryExecutorService;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import org.jetbrains.annotations.NotNull;

public final class BackpressureMonitor implements IBackpressureMonitor, Runnable {
  static final int MAX_DOWNSAMPLE_FACTOR = 10;
  private static final int INITIAL_CHECK_DELAY_IN_MS = 500;
  private static final int CHECK_INTERVAL_IN_MS = 10 * 1000;

  private final @NotNull SentryOptions sentryOptions;
  private final @NotNull IScopes scopes;
  private int downsampleFactor = 0;

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
      executorService.schedule(this, delay);
    }
  }

  private boolean isHealthy() {
    return scopes.isHealthy();
  }
}
