package io.sentry.core;

import static io.sentry.core.SentryLevel.ERROR;
import static io.sentry.core.cache.SessionCache.PREFIX_CURRENT_SESSION_FILE;

import io.sentry.core.hints.Cached;
import io.sentry.core.hints.RetryableHint;
import io.sentry.core.hints.SubmissionResult;
import io.sentry.core.util.Objects;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class EnvelopeSender extends DirectoryProcessor implements IEnvelopeSender {

  @SuppressWarnings("CharsetObjectCanBeUsed")
  private static final Charset UTF_8 = Charset.forName("UTF-8");

  private final @NotNull IHub hub;
  private final @NotNull IEnvelopeReader envelopeReader;
  private final @NotNull ISerializer serializer;
  private final @NotNull ILogger logger;

  public EnvelopeSender(
      final @NotNull IHub hub,
      final @NotNull IEnvelopeReader envelopeReader,
      final @NotNull ISerializer serializer,
      final @NotNull ILogger logger) {
    super(logger);
    this.hub = Objects.requireNonNull(hub, "Hub is required.");
    this.envelopeReader = Objects.requireNonNull(envelopeReader, "Envelope reader is required.");
    this.serializer = Objects.requireNonNull(serializer, "Serializer is required.");
    this.logger = Objects.requireNonNull(logger, "Logger is required.");
  }

  @Override
  protected void processFile(final @NotNull File file) {
    Objects.requireNonNull(file, "File is required.");

    if (!isRelevantFileName(file.getName())) {
      logger.log(SentryLevel.DEBUG, "File '%s' should be ignored.", file.getName());
      return;
    }

    final CachedEnvelopeHint hint =
        new CachedEnvelopeHint(15000, logger); // TODO: Take timeout from options
    try (InputStream stream = new FileInputStream(file)) {
      final SentryEnvelope envelope = envelopeReader.read(stream);
      if (envelope == null) {
        logger.log(
            SentryLevel.ERROR,
            "Stream from path %s resulted in a null envelope.",
            file.getAbsolutePath());
      } else {
        processEnvelope(envelope, hint);
      }
    } catch (IOException e) {
      logger.log(SentryLevel.ERROR, "Error processing envelope.", e);
    } finally {
      if (!hint.getRetry()) {
        try {
          file.delete();
        } catch (RuntimeException e) {
          logger.log(SentryLevel.ERROR, "Failed to delete.", e);
        }
      }
    }
  }

  @Override
  protected boolean isRelevantFileName(final @Nullable String fileName) {
    // ignore current.envelope
    return fileName != null && !fileName.startsWith(PREFIX_CURRENT_SESSION_FILE);
    // TODO: Use an extension to filter out relevant files
  }

  @Override
  public void processEnvelopeFile(@NotNull String path) {
    Objects.requireNonNull(path, "Path is required.");

    processFile(new File(path));
  }

  private void processEnvelope(
      final @NotNull SentryEnvelope envelope, final @NotNull CachedEnvelopeHint hint)
      throws IOException {
    logger.log(SentryLevel.DEBUG, "Envelope for event Id: %s", envelope.getHeader().getEventId());
    int items = 0;
    for (final SentryEnvelopeItem item : envelope.getItems()) {
      items++;

      if (item.getHeader() == null) {
        logger.log(SentryLevel.ERROR, "Item %d has no header", items);
        continue;
      }
      if (SentryEnvelopeItemType.Event.getType().equals(item.getHeader().getType())) {
        try (final Reader eventReader =
            new InputStreamReader(new ByteArrayInputStream(item.getData()), UTF_8)) {
          SentryEvent event = serializer.deserializeEvent(eventReader);
          if (event == null) {
            logger.log(
                SentryLevel.ERROR,
                "Item %d of type %s returned null by the parser.",
                items,
                item.getHeader().getType());
          } else {
            if (!envelope.getHeader().getEventId().equals(event.getEventId())) {
              logger.log(
                  SentryLevel.ERROR,
                  "Item %d of has a different event id (%s) to the envelope header (%s)",
                  items,
                  envelope.getHeader().getEventId(),
                  event.getEventId());
              continue;
            }
            hub.captureEvent(event, hint);
            logger.log(SentryLevel.DEBUG, "Item %d is being captured.", items);
            if (!hint.waitFlush()) {
              logger.log(
                  SentryLevel.WARNING,
                  "Timed out waiting for event submission: %s",
                  event.getEventId());
              break;
            }
          }
        } catch (Exception e) {
          logger.log(ERROR, "Item failed to process.", e);
        }
      } else if (SentryEnvelopeItemType.Session.getType().equals(item.getHeader().getType())) {
        try (final Reader reader =
            new InputStreamReader(new ByteArrayInputStream(item.getData()), UTF_8)) {
          final Session session = serializer.deserializeSession(reader);
          if (session == null) {
            logger.log(
                SentryLevel.ERROR,
                "Item %d of type %s returned null by the parser.",
                items,
                item.getHeader().getType());
          } else {
            // capture 1 per 1 to be easier for now
            hub.captureEnvelope(SentryEnvelope.fromSession(serializer, session), hint);
            logger.log(SentryLevel.DEBUG, "Item %d is being captured.", items);
            if (!hint.waitFlush()) {
              logger.log(
                  SentryLevel.WARNING,
                  "Timed out waiting for item submission: %s",
                  session.getSessionId());
              break;
            }
          }
        } catch (Exception e) {
          logger.log(ERROR, "Item failed to process.", e);
        }
      } else {
        // TODO: Handle attachments and other types
        logger.log(
            SentryLevel.WARNING, "Item %d of type: %s ignored.", items, item.getHeader().getType());
      }

      if (!hint.succeeded) {
        // Failed to send an item of the envelope: Stop attempting to send the rest (an attachment
        // without the event that created it isn't useful)
        logger.log(
            SentryLevel.WARNING,
            "Envelope for event Id: %s had a failed capture at item %d. No more items will be sent.",
            envelope.getHeader().getEventId(),
            items);
        break;
      }
      hint.reset();
    }
  }

  private static final class CachedEnvelopeHint implements Cached, RetryableHint, SubmissionResult {
    boolean retry = false;
    boolean succeeded = false;

    private @NotNull CountDownLatch latch;
    private final long timeoutMills;
    private final @NotNull ILogger logger;

    CachedEnvelopeHint(final long timeoutMills, final @NotNull ILogger logger) {
      this.timeoutMills = timeoutMills;
      this.latch = new CountDownLatch(1);
      this.logger = Objects.requireNonNull(logger, "ILogger is required.");
    }

    boolean waitFlush() {
      try {
        return latch.await(timeoutMills, TimeUnit.MILLISECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        logger.log(ERROR, "Exception while awaiting on lock.", e);
      }
      return false;
    }

    public void reset() {
      latch = new CountDownLatch(1);
      succeeded = false;
    }

    @Override
    public boolean getRetry() {
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
  }
}
