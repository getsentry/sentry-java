package io.sentry.hints;

import static io.sentry.SentryLevel.ERROR;

import io.sentry.ILogger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public abstract class BlockingFlushHint implements DiskFlushNotification, Flushable {

  private final CountDownLatch latch;
  private final long flushTimeoutMillis;
  private final @NotNull ILogger logger;

  public BlockingFlushHint(final long flushTimeoutMillis, final @NotNull ILogger logger) {
    this.flushTimeoutMillis = flushTimeoutMillis;
    latch = new CountDownLatch(1);
    this.logger = logger;
  }

  @Override
  public boolean waitFlush() {
    try {
      return latch.await(flushTimeoutMillis, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      if (logger.isEnabled(ERROR)) {
        logger.log(ERROR, "Exception while awaiting for flush in BlockingFlushHint", e);
      }
    }
    return false;
  }

  @Override
  public void markFlushed() {
    latch.countDown();
  }
}
