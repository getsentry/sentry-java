package io.sentry;

import io.sentry.cache.EnvelopeCache;
import io.sentry.hints.Flushable;
import io.sentry.hints.Retryable;
import io.sentry.util.FileUtils;
import io.sentry.util.HintUtils;
import io.sentry.util.Objects;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class EnvelopeSender extends DirectoryProcessor implements IEnvelopeSender {

  private final @NotNull IScopes scopes;
  private final @NotNull ISerializer serializer;
  private final @NotNull ILogger logger;

  public EnvelopeSender(
      final @NotNull IScopes scopes,
      final @NotNull ISerializer serializer,
      final @NotNull ILogger logger,
      final long flushTimeoutMillis,
      final int maxQueueSize) {
    super(scopes, logger, flushTimeoutMillis, maxQueueSize);
    this.scopes = Objects.requireNonNull(scopes, "Scopes are required.");
    this.serializer = Objects.requireNonNull(serializer, "Serializer is required.");
    this.logger = Objects.requireNonNull(logger, "Logger is required.");
  }

  @Override
  protected void processFile(final @NotNull File file, final @NotNull Hint hint) {
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

    // Set once the send is still in flight and the hint has taken over the file, so that the
    // decision below is left to whoever learns the outcome of the send.
    final AtomicBoolean deferredToHint = new AtomicBoolean(false);

    try (final InputStream is = new BufferedInputStream(new FileInputStream(file))) {
      SentryEnvelope envelope = serializer.deserializeEnvelope(is);
      if (envelope == null) {
        logger.log(
            SentryLevel.ERROR, "Failed to deserialize cached envelope %s", file.getAbsolutePath());
      } else {
        scopes.captureEnvelope(envelope, hint);

        if (HintUtils.isCaptureFailed(hint)) {
          // The envelope never reached the transport, so nothing will ever report an outcome for
          // it. Keep the file for a later attempt instead of awaiting a flush that cannot come.
          HintUtils.runIfHasTypeLogIfNot(
              hint, Retryable.class, logger, (retryable) -> retryable.setRetry(true));
          return;
        }
      }

      HintUtils.runIfHasTypeLogIfNot(
          hint,
          Flushable.class,
          logger,
          (flushable) -> {
            if (!flushable.waitFlush()) {
              logger.log(SentryLevel.WARNING, "Timed out waiting for envelope submission.");
              deferredToHint.set(HintUtils.deferDelete(hint, file));
            }
          });
    } catch (FileNotFoundException e) {
      logger.log(SentryLevel.ERROR, e, "File '%s' cannot be found.", file.getAbsolutePath());
    } catch (IOException e) {
      logger.log(SentryLevel.ERROR, e, "I/O on file '%s' failed.", file.getAbsolutePath());
    } catch (Throwable e) {
      logger.log(
          SentryLevel.ERROR, e, "Failed to capture cached envelope %s", file.getAbsolutePath());
      HintUtils.runIfHasTypeLogIfNot(
          hint,
          Retryable.class,
          logger,
          (retryable) -> {
            retryable.setRetry(false);
            logger.log(SentryLevel.INFO, e, "File '%s' won't retry.", file.getAbsolutePath());
          });
    } finally {
      if (deferredToHint.get()) {
        logger.log(
            SentryLevel.INFO,
            "File %s kept until the in-flight send reports its outcome.",
            file.getAbsolutePath());
      } else {
        // Unless the transport marked this to be retried, it'll be deleted.
        HintUtils.runIfHasTypeLogIfNot(
            hint,
            Retryable.class,
            logger,
            (retryable) -> {
              if (!retryable.isRetry()) {
                FileUtils.deleteFile(file, logger);
              } else {
                logger.log(
                    SentryLevel.INFO,
                    "File not deleted since retry was marked. %s.",
                    file.getAbsolutePath());
              }
            });
      }
    }
  }

  @Override
  protected boolean isRelevantFileName(final @NotNull String fileName) {
    return fileName.endsWith(EnvelopeCache.SUFFIX_ENVELOPE_FILE);
  }

  @Override
  public void processEnvelopeFile(final @NotNull String path, final @NotNull Hint hint) {
    Objects.requireNonNull(path, "Path is required.");

    processFile(new File(path), hint);
  }
}
