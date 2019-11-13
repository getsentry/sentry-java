package io.sentry.core;

import io.sentry.core.util.Objects;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import org.jetbrains.annotations.NotNull;

public final class EnvelopeSender implements IEnvelopeSender {

  @SuppressWarnings("CharsetObjectCanBeUsed")
  private static final Charset UTF_8 = Charset.forName("UTF-8");

  private final IHub hub;
  private final IEnvelopeReader envelopeReader;
  private final ISerializer serializer;
  private final ILogger logger;

  public EnvelopeSender(
      IHub hub, IEnvelopeReader envelopeReader, ISerializer serializer, ILogger logger) {
    this.hub = Objects.requireNonNull(hub, "Hub is required.");
    this.envelopeReader = Objects.requireNonNull(envelopeReader, "Envelope reader is required.");
    this.serializer = Objects.requireNonNull(serializer, "Serializer is required.");
    this.logger = Objects.requireNonNull(logger, "Logger is required.");
  }

  @Override
  public void processEnvelopeFile(@NotNull String path) {
    InputStream stream = null;
    File file = null;
    try {
      file = new File(path);
      stream = new FileInputStream(file);
      SentryEnvelope envelope = envelopeReader.read(stream);
      if (envelope == null) {
        logger.log(SentryLevel.ERROR, "Stream from path %s resulted in a null envelope.", path);
      } else {
        processEnvelope(envelope);
      }
    } catch (Exception e) {
      logger.log(SentryLevel.ERROR, "Error processing envelope.", e);
    } finally {
      try {
        if (stream != null) stream.close();
      } catch (IOException ex) {
        logger.log(SentryLevel.ERROR, "Error closing envelope.", ex);
      }
      if (file != null) {
        // TODO: Handle error, at least ignore in memory
        try {
          file.delete();
        } catch (Exception e) {
          logger.log(SentryLevel.ERROR, "Failed to delete.", e);
        }
      }
    }
  }

  private void processEnvelope(SentryEnvelope envelope) throws IOException {
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
            hub.captureEvent(event);
            logger.log(SentryLevel.DEBUG, "Item %d is being captured.", items);
          }
        }
      } else {
        // TODO: Handle attachments and other types
        logger.log(
            SentryLevel.WARNING, "Item %d of type: %s ignored.", items, item.getHeader().getType());
      }
    }
  }
}
