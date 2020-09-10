package io.sentry;

import io.sentry.cache.EnvelopeCache;
import io.sentry.hints.Flushable;
import io.sentry.hints.Retryable;
import io.sentry.util.LogUtils;
import io.sentry.util.Objects;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class EnvelopeSender extends DirectoryProcessor implements IEnvelopeSender {

  private final @NotNull IHub hub;
  private final @NotNull ISerializer serializer;
  private final @NotNull ILogger logger;

  public EnvelopeSender(
      final @NotNull IHub hub,
      final @NotNull ISerializer serializer,
      final @NotNull ILogger logger,
      final long flushTimeoutMillis) {
    super(logger, flushTimeoutMillis);
    this.hub = Objects.requireNonNull(hub, "Hub is required.");
    this.serializer = Objects.requireNonNull(serializer, "Serializer is required.");
    this.logger = Objects.requireNonNull(logger, "Logger is required.");
  }

  @Override
  protected void processFile(@NotNull File file, @Nullable Object hint) {
    if (!file.isFile()) {
      logger.log(SentryLevel.DEBUG, "'%s' is not a file.", file.getAbsolutePath());
      return;
    }

    if (!isRelevantFileName(file.getName())) {
      logger.log(
          SentryLevel.DEBUG, "File '%s' doesn't match extension expected.", file.getAbsolutePath());
      return;
    }

    if (!file.getParentFile().canWrite()) {
      logger.log(
          SentryLevel.WARNING,
          "File '%s' cannot be deleted so it will not be processed.",
          file.getAbsolutePath());
      return;
    }

    try (final InputStream is = new BufferedInputStream(new FileInputStream(file))) {
      SentryEnvelope envelope = serializer.deserializeEnvelope(is);
      hub.captureEnvelope(envelope, hint);

      if (hint instanceof Flushable) {
        if (!((Flushable) hint).waitFlush()) {
          logger.log(SentryLevel.WARNING, "Timed out waiting for envelope submission.");
        }
      } else {
        LogUtils.logIfNotFlushable(logger, hint);
      }
    } catch (FileNotFoundException e) {
      logger.log(SentryLevel.ERROR, e, "File '%s' cannot be found.", file.getAbsolutePath());
    } catch (IOException e) {
      logger.log(SentryLevel.ERROR, e, "I/O on file '%s' failed.", file.getAbsolutePath());
    } catch (Exception e) {
      logger.log(
          SentryLevel.ERROR, e, "Failed to capture cached envelope %s", file.getAbsolutePath());
      if (hint instanceof Retryable) {
        ((Retryable) hint).setRetry(false);
        logger.log(SentryLevel.INFO, e, "File '%s' won't retry.", file.getAbsolutePath());
      } else {
        LogUtils.logIfNotRetryable(logger, hint);
      }
    } finally {
      // Unless the transport marked this to be retried, it'll be deleted.
      if (hint instanceof Retryable) {
        if (!((Retryable) hint).isRetry()) {
          safeDelete(file, "after trying to capture it");
          logger.log(SentryLevel.DEBUG, "Deleted file %s.", file.getAbsolutePath());
        } else {
          logger.log(
              SentryLevel.INFO,
              "File not deleted since retry was marked. %s.",
              file.getAbsolutePath());
        }
      } else {
        LogUtils.logIfNotRetryable(logger, hint);
      }
    }
  }

  @Override
  protected boolean isRelevantFileName(String fileName) {
    return fileName.endsWith(EnvelopeCache.SUFFIX_ENVELOPE_FILE);
  }

  @Override
  public void processEnvelopeFile(@NotNull String path, @Nullable Object hint) {
    Objects.requireNonNull(path, "Path is required.");

    processFile(new File(path), hint);
  }

  private void safeDelete(File file, String errorMessageSuffix) {
    try {
      if (!file.delete()) {
        logger.log(
            SentryLevel.ERROR,
            "Failed to delete '%s' %s",
            file.getAbsolutePath(),
            errorMessageSuffix);
      }
    } catch (Exception e) {
      logger.log(
          SentryLevel.ERROR,
          e,
          "Failed to delete '%s' %s",
          file.getAbsolutePath(),
          errorMessageSuffix);
    }
  }
}
