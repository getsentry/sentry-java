package io.sentry.core;

import static io.sentry.core.SentryLevel.ERROR;

import io.sentry.core.hints.Cached;
import io.sentry.core.hints.Retryable;
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

@ApiStatus.Internal
public final class EnvelopeSender extends DirectoryProcessor implements IEnvelopeSender {

  @SuppressWarnings("CharsetObjectCanBeUsed")
  private static final Charset UTF_8 = Charset.forName("UTF-8");

  private final IHub hub;
  private final IEnvelopeReader envelopeReader;
  private final ISerializer serializer;
  private final @NotNull ILogger logger;

  public EnvelopeSender(
      IHub hub,
      IEnvelopeReader envelopeReader,
      ISerializer serializer,
      final @NotNull ILogger logger) {
    super(logger);
    this.hub = Objects.requireNonNull(hub, "Hub is required.");
    this.envelopeReader = Objects.requireNonNull(envelopeReader, "Envelope reader is required.");
    this.serializer = Objects.requireNonNull(serializer, "Serializer is required.");
    this.logger = Objects.requireNonNull(logger, "Logger is required.");
  }

  @Override
  protected void processFile(@NotNull File file) {
    CachedEnvelopeHint hint =
        new CachedEnvelopeHint(15000, logger); // TODO: Take timeout from options
    try (InputStream stream = new FileInputStream(file)) {
      SentryEnvelope envelope = envelopeReader.read(stream);
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
      if (file != null && !hint.getRetry()) {
        try {
          file.delete();
        } catch (RuntimeException e) {
          logger.log(SentryLevel.ERROR, "Failed to delete.", e);
        }
      }
    }
  }

  @Override
  protected boolean isRelevantFileName(String fileName) {
    return true; // TODO: Use an extension to filter out relevant files
  }

  @Override
  public void processEnvelopeFile(@NotNull String path) {
    processFile(new File(path));
  }

  private void processEnvelope(@NotNull SentryEnvelope envelope, @NotNull CachedEnvelopeHint hint)
      throws IOException {
    logger.log(SentryLevel.DEBUG, "Envelope for event Id: %s", envelope.getHeader().getEventId());
    int items = 0;
    for (SentryEnvelopeItem item : envelope.getItems()) {
      items++;

      if (item.getHeader() == null) {
        logger.log(SentryLevel.ERROR, "Item %d has no header", items);
        continue;
      }
      if ("event".equals(item.getHeader().getType())) {
        try (Reader eventReader =
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

  private static final class CachedEnvelopeHint implements Cached, Retryable, SubmissionResult {
    boolean retry = false;
    boolean succeeded = false;

    private CountDownLatch latch;
    private final long timeoutMills;
    private final @NotNull ILogger logger;

    CachedEnvelopeHint(final long timeoutMills, final @NotNull ILogger logger) {
      this.timeoutMills = timeoutMills;
      this.latch = new CountDownLatch(1);
      this.logger = logger;
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
