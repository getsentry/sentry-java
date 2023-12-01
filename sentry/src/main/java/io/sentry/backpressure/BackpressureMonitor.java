package io.sentry.backpressure;

import io.sentry.ISentryExecutorService;
import io.sentry.Sentry;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.jetbrains.annotations.NotNull;

public final class BackpressureMonitor implements IBackpressureMonitor, Runnable {
  private static final int MAX_DOWNSAMPLE_FACTOR = 10;
  private static final int CHECK_INTERVAL_IN_MS = 10 * 1000;

  private final @NotNull SentryOptions sentryOptions;
  private int downsampleFactor = 0;
  private boolean didEverDownsample = false;

  public BackpressureMonitor(final @NotNull SentryOptions sentryOptions) {
    this.sentryOptions = sentryOptions;
  }

  @Override
  public void start() {
    reschedule();
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  private void reschedule() {
    final @NotNull ISentryExecutorService executorService = sentryOptions.getExecutorService();
    if (!executorService.isClosed()) {
      executorService.schedule(this, CHECK_INTERVAL_IN_MS);
    }
  }

  @Override
  public int getDownsampleFactor() {
    return downsampleFactor;
  }

  @Override
  public void run() {
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
        didEverDownsample = true;
        sentryOptions
            .getLogger()
            .log(
                SentryLevel.DEBUG,
                "Health check negative, downsampling with a factor of %d",
                downsampleFactor);
      }
    }
    System.out.println(
        "hello from backpressure monitor, downsamplingFactor is now "
            + downsampleFactor
            + " and it is "
            + ZonedDateTime.now(ZoneId.systemDefault()).toString()
            + " didEverDownsample? "
            + didEverDownsample);
    reschedule();
  }

  private boolean isHealthy() {
    return Sentry.isHealthy();
  }
}
