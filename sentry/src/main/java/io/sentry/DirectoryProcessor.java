package io.sentry;

import static io.sentry.SentryLevel.DEBUG;
import static io.sentry.SentryLevel.ERROR;
import static io.sentry.SentryLevel.WARNING;

import io.sentry.hints.Cached;
import io.sentry.hints.Enqueable;
import io.sentry.hints.Flushable;
import io.sentry.hints.Retryable;
import io.sentry.hints.SubmissionResult;
import io.sentry.transport.RateLimiter;
import io.sentry.util.HintUtils;
import java.io.File;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract class DirectoryProcessor {

  private static final long ENVELOPE_PROCESSING_DELAY = 100L;
  private final @NotNull IScopes scopes;
  private final @NotNull ILogger logger;
  private final long flushTimeoutMillis;
  private final Queue<String> processedEnvelopes;

  DirectoryProcessor(
      final @NotNull IScopes scopes,
      final @NotNull ILogger logger,
      final long flushTimeoutMillis,
      final int maxQueueSize) {
    this.scopes = scopes;
    this.logger = logger;
    this.flushTimeoutMillis = flushTimeoutMillis;
    this.processedEnvelopes =
        SynchronizedQueue.synchronizedQueue(new CircularFifoQueue<>(maxQueueSize));
  }

  public void processDirectory(final @NotNull File directory) {
    try {
      if (logger.isEnabled(DEBUG)) {
        logger.log(SentryLevel.DEBUG, "Processing dir. %s", directory.getAbsolutePath());
      }

      if (!directory.exists()) {
        if (logger.isEnabled(WARNING)) {
          logger.log(
              SentryLevel.WARNING,
              "Directory '%s' doesn't exist. No cached events to send.",
              directory.getAbsolutePath());
        }
        return;
      }
      if (!directory.isDirectory()) {
        if (logger.isEnabled(ERROR)) {
          logger.log(
              SentryLevel.ERROR, "Cache dir %s is not a directory.", directory.getAbsolutePath());
        }
        return;
      }

      final File[] listFiles = directory.listFiles();
      if (listFiles == null) {
        if (logger.isEnabled(ERROR)) {
          logger.log(SentryLevel.ERROR, "Cache dir %s is null.", directory.getAbsolutePath());
        }
        return;
      }

      final File[] filteredListFiles = directory.listFiles((d, name) -> isRelevantFileName(name));

      if (logger.isEnabled(DEBUG)) {
        logger.log(
            SentryLevel.DEBUG,
            "Processing %d items from cache dir %s",
            filteredListFiles != null ? filteredListFiles.length : 0,
            directory.getAbsolutePath());
      }

      for (File file : listFiles) {
        // it ignores .sentry-native database folder and new ones that might come up
        if (!file.isFile()) {
          if (logger.isEnabled(DEBUG)) {
            logger.log(SentryLevel.DEBUG, "File %s is not a File.", file.getAbsolutePath());
          }
          continue;
        }

        final String filePath = file.getAbsolutePath();
        // if envelope has already been submitted into the transport queue, we don't process it
        // again
        if (processedEnvelopes.contains(filePath)) {
          if (logger.isEnabled(DEBUG)) {
            logger.log(
                SentryLevel.DEBUG,
                "File '%s' has already been processed so it will not be processed again.",
                filePath);
          }
          continue;
        }

        // in case there's rate limiting active, skip processing
        final @Nullable RateLimiter rateLimiter = scopes.getRateLimiter();
        if (rateLimiter != null && rateLimiter.isActiveForCategory(DataCategory.All)) {
          if (logger.isEnabled(SentryLevel.INFO)) {
            logger.log(SentryLevel.INFO, "DirectoryProcessor, rate limiting active.");
          }
          return;
        }

        if (logger.isEnabled(DEBUG)) {
          logger.log(SentryLevel.DEBUG, "Processing file: %s", filePath);
        }

        final SendCachedEnvelopeHint cachedHint =
            new SendCachedEnvelopeHint(flushTimeoutMillis, logger, filePath, processedEnvelopes);

        final Hint hint = HintUtils.createWithTypeCheckHint(cachedHint);
        processFile(file, hint);

        // a short delay between processing envelopes to avoid bursting our server and hitting
        // another rate limit https://develop.sentry.dev/sdk/features/#additional-capabilities
        // InterruptedException will be handled by the outer try-catch
        Thread.sleep(ENVELOPE_PROCESSING_DELAY);
      }
    } catch (Throwable e) {
      if (logger.isEnabled(ERROR)) {
        logger.log(SentryLevel.ERROR, e, "Failed processing '%s'", directory.getAbsolutePath());
      }
    }
  }

  protected abstract void processFile(final @NotNull File file, final @NotNull Hint hint);

  protected abstract boolean isRelevantFileName(String fileName);

  private static final class SendCachedEnvelopeHint
      implements Cached, Retryable, SubmissionResult, Flushable, Enqueable {
    boolean retry = false;
    boolean succeeded = false;

    private final CountDownLatch latch;
    private final long flushTimeoutMillis;
    private final @NotNull ILogger logger;
    private final @NotNull String filePath;
    private final @NotNull Queue<String> processedEnvelopes;

    public SendCachedEnvelopeHint(
        final long flushTimeoutMillis,
        final @NotNull ILogger logger,
        final @NotNull String filePath,
        final @NotNull Queue<String> processedEnvelopes) {
      this.flushTimeoutMillis = flushTimeoutMillis;
      this.filePath = filePath;
      this.processedEnvelopes = processedEnvelopes;
      this.latch = new CountDownLatch(1);
      this.logger = logger;
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
    public boolean waitFlush() {
      try {
        return latch.await(flushTimeoutMillis, TimeUnit.MILLISECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        if (logger.isEnabled(ERROR)) {
          logger.log(ERROR, "Exception while awaiting on lock.", e);
        }
      }
      return false;
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

    @Override
    public void markEnqueued() {
      processedEnvelopes.add(filePath);
    }
  }
}
