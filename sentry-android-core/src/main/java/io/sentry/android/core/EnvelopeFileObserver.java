package io.sentry.android.core;

import static io.sentry.SentryLevel.ERROR;

import android.os.FileObserver;
import io.sentry.IEnvelopeSender;
import io.sentry.ILogger;
import io.sentry.SentryLevel;
import io.sentry.hints.ApplyScopeData;
import io.sentry.hints.Cached;
import io.sentry.hints.Flushable;
import io.sentry.hints.Retryable;
import io.sentry.hints.SubmissionResult;
import io.sentry.util.Objects;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class EnvelopeFileObserver extends FileObserver {

  private final String rootPath;
  private final IEnvelopeSender envelopeSender;
  private @NotNull final ILogger logger;
  private final long flushTimeoutMillis;

  // The preferred overload (Taking File instead of String) is only available from API 29
  @SuppressWarnings("deprecation")
  EnvelopeFileObserver(
      String path,
      IEnvelopeSender envelopeSender,
      @NotNull ILogger logger,
      final long flushTimeoutMillis) {
    super(path);
    this.rootPath = Objects.requireNonNull(path, "File path is required.");
    this.envelopeSender = Objects.requireNonNull(envelopeSender, "Envelope sender is required.");
    this.logger = Objects.requireNonNull(logger, "Logger is required.");
    this.flushTimeoutMillis = flushTimeoutMillis;
  }

  @Override
  public void onEvent(int eventType, @Nullable String relativePath) {
    if (relativePath == null || eventType != FileObserver.CLOSE_WRITE) {
      return;
    }

    logger.log(
        SentryLevel.DEBUG,
        "onEvent fired for EnvelopeFileObserver with event type %d on path: %s for file %s.",
        eventType,
        this.rootPath,
        relativePath);

    // TODO: Only some event types should be pass through?

    final CachedEnvelopeHint hint = new CachedEnvelopeHint(flushTimeoutMillis, logger);
    envelopeSender.processEnvelopeFile(this.rootPath + File.separator + relativePath, hint);
  }

  private static final class CachedEnvelopeHint
      implements Cached, Retryable, SubmissionResult, Flushable, ApplyScopeData {
    boolean retry = false;
    boolean succeeded = false;

    private @NotNull final CountDownLatch latch;
    private final long flushTimeoutMillis;
    private final @NotNull ILogger logger;

    public CachedEnvelopeHint(final long flushTimeoutMillis, final @NotNull ILogger logger) {
      this.flushTimeoutMillis = flushTimeoutMillis;
      this.latch = new CountDownLatch(1);
      this.logger = Objects.requireNonNull(logger, "ILogger is required.");
    }

    @Override
    public boolean waitFlush() {
      try {
        return latch.await(flushTimeoutMillis, TimeUnit.MILLISECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        logger.log(ERROR, "Exception while awaiting on lock.", e);
      }
      return false;
    }

    @Override
    public boolean isRetry() {
      return retry;
    }

    @Override
    public void setRetry(boolean retry) {
      this.retry = retry;
    }

    @Override
    public void setResult(boolean succeeded) {
      this.succeeded = succeeded;
      latch.countDown();
    }

    @Override
    public boolean isSuccess() {
      return succeeded;
    }
  }
}
