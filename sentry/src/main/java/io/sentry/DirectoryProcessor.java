package io.sentry;

import static io.sentry.SentryLevel.ERROR;

import io.sentry.hints.Cached;
import io.sentry.hints.Flushable;
import io.sentry.hints.Retryable;
import io.sentry.hints.SubmissionResult;
import io.sentry.util.HintUtils;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

abstract class DirectoryProcessor {

  private final @NotNull ILogger logger;
  private final long flushTimeoutMillis;

  DirectoryProcessor(final @NotNull ILogger logger, final long flushTimeoutMillis) {
    this.logger = logger;
    this.flushTimeoutMillis = flushTimeoutMillis;
  }

  public void processDirectory(final @NotNull File directory) {
    try {
      logger.log(SentryLevel.DEBUG, "Processing dir. %s", directory.getAbsolutePath());

      if (!directory.exists()) {
        logger.log(
            SentryLevel.WARNING,
            "Directory '%s' doesn't exist. No cached events to send.",
            directory.getAbsolutePath());
        return;
      }
      if (!directory.isDirectory()) {
        logger.log(
            SentryLevel.ERROR, "Cache dir %s is not a directory.", directory.getAbsolutePath());
        return;
      }

      final File[] listFiles = directory.listFiles();
      if (listFiles == null) {
        logger.log(SentryLevel.ERROR, "Cache dir %s is null.", directory.getAbsolutePath());
        return;
      }

      final File[] filteredListFiles = directory.listFiles((d, name) -> isRelevantFileName(name));

      logger.log(
          SentryLevel.DEBUG,
          "Processing %d items from cache dir %s",
          filteredListFiles != null ? filteredListFiles.length : 0,
          directory.getAbsolutePath());

      for (File file : listFiles) {
        // it ignores .sentry-native database folder and new ones that might come up
        if (!file.isFile()) {
          logger.log(SentryLevel.DEBUG, "File %s is not a File.", file.getAbsolutePath());
          continue;
        }

        logger.log(SentryLevel.DEBUG, "Processing file: %s", file.getAbsolutePath());

        final SendCachedEnvelopeHint cachedHint =
            new SendCachedEnvelopeHint(flushTimeoutMillis, logger);

        final Hint hint = HintUtils.createWithTypeCheckHint(cachedHint);
        hint.set(TypeCheckHint.SENTRY_CACHED_ENVELOPE_FILE_PATH, file.getAbsolutePath());
        processFile(file, hint);
      }
    } catch (Throwable e) {
      logger.log(SentryLevel.ERROR, e, "Failed processing '%s'", directory.getAbsolutePath());
    }
  }

  protected abstract void processFile(final @NotNull File file, final @NotNull Hint hint);

  protected abstract boolean isRelevantFileName(String fileName);

  private static final class SendCachedEnvelopeHint
      implements Cached, Retryable, SubmissionResult, Flushable {
    boolean retry = false;
    boolean succeeded = false;

    private final CountDownLatch latch;
    private final long flushTimeoutMillis;
    private final @NotNull ILogger logger;

    public SendCachedEnvelopeHint(final long flushTimeoutMillis, final @NotNull ILogger logger) {
      this.flushTimeoutMillis = flushTimeoutMillis;
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
        logger.log(ERROR, "Exception while awaiting on lock.", e);
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
  }
}
