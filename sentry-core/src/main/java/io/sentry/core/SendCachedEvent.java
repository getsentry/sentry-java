package io.sentry.core;

import io.sentry.core.cache.DiskCache;
import io.sentry.core.hints.Flushable;
import io.sentry.core.hints.Retryable;
import io.sentry.core.util.Objects;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class SendCachedEvent extends DirectoryProcessor {
  private static final Charset UTF_8 = Charset.forName("UTF-8");
  private final ISerializer serializer;
  private final IHub hub;
  private final @NotNull ILogger logger;

  SendCachedEvent(
      @NotNull ISerializer serializer,
      @NotNull IHub hub,
      final @NotNull ILogger logger,
      final long flushTimeoutMillis) {
    super(logger, flushTimeoutMillis);
    this.serializer = Objects.requireNonNull(serializer, "Serializer is required.");
    this.hub = Objects.requireNonNull(hub, "Hub is required.");
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
          "File '%s' cannot be delete so it will not be processed.",
          file.getAbsolutePath());
      return;
    }

    try (final Reader reader =
        new BufferedReader(new InputStreamReader(new FileInputStream(file), UTF_8))) {
      SentryEvent event = serializer.deserializeEvent(reader);
      hub.captureEvent(event, hint);

      if (hint instanceof Flushable) {
        if (!((Flushable) hint).waitFlush()) {
          logger.log(
              SentryLevel.WARNING,
              "Timed out waiting for event submission: %s",
              event.getEventId());
        }
      } else {
        logger.log(SentryLevel.DEBUG, "File %s is not Flushable", file.getAbsolutePath());
      }
    } catch (FileNotFoundException e) {
      logger.log(SentryLevel.ERROR, e, "File '%s' cannot be found.", file.getAbsolutePath());
    } catch (IOException e) {
      logger.log(SentryLevel.ERROR, e, "I/O on file '%s' failed.", file.getAbsolutePath());
    } catch (Exception e) {
      logger.log(SentryLevel.ERROR, e, "Failed to capture cached event %s", file.getAbsolutePath());
      if (hint instanceof Retryable) {
        ((Retryable) hint).setRetry(false);
        logger.log(SentryLevel.INFO, e, "File '%s' won't retry.", file.getAbsolutePath());
      } else {
        logger.log(SentryLevel.DEBUG, "File %s is not Retryable", file.getAbsolutePath());
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
      }
    }
  }

  @Override
  protected boolean isRelevantFileName(String fileName) {
    return fileName.endsWith(DiskCache.FILE_SUFFIX);
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
