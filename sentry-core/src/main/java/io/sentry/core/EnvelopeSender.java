package io.sentry.core;

import static io.sentry.core.SentryLevel.ERROR;
import static io.sentry.core.cache.SessionCache.PREFIX_CURRENT_SESSION_FILE;

import io.sentry.core.hints.Flushable;
import io.sentry.core.hints.Retryable;
import io.sentry.core.hints.SubmissionResult;
import io.sentry.core.util.CollectionUtils;
import io.sentry.core.util.Objects;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
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
      final @NotNull ILogger logger,
      final long flushTimeoutMillis) {
    super(logger, flushTimeoutMillis);
    this.hub = Objects.requireNonNull(hub, "Hub is required.");
    this.envelopeReader = Objects.requireNonNull(envelopeReader, "Envelope reader is required.");
    this.serializer = Objects.requireNonNull(serializer, "Serializer is required.");
    this.logger = Objects.requireNonNull(logger, "Logger is required.");
  }

  @Override
  protected void processFile(final @NotNull File file, @Nullable Object hint) {
    Objects.requireNonNull(file, "File is required.");

    if (!isRelevantFileName(file.getName())) {
      logger.log(SentryLevel.DEBUG, "File '%s' should be ignored.", file.getName());
      return;
    }

    try (final InputStream stream = new BufferedInputStream(new FileInputStream(file))) {
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
      if ((hint instanceof Retryable) && !((Retryable) hint).isRetry()) {
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
  public void processEnvelopeFile(@NotNull String path, @Nullable Object hint) {
    Objects.requireNonNull(path, "Path is required.");

    processFile(new File(path), hint);
  }

  private void processEnvelope(final @NotNull SentryEnvelope envelope, final @Nullable Object hint)
      throws IOException {
    logger.log(
        SentryLevel.DEBUG,
        "Processing Envelope with %d item(s)",
        CollectionUtils.size(envelope.getItems()));
    int items = 0;
    for (final SentryEnvelopeItem item : envelope.getItems()) {
      items++;

      if (item.getHeader() == null) {
        logger.log(SentryLevel.ERROR, "Item %d has no header", items);
        continue;
      }
      if (SentryEnvelopeItemType.Event.getType().equals(item.getHeader().getType())) {
        try (final Reader eventReader =
            new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(item.getData()), UTF_8))) {
          SentryEvent event = serializer.deserializeEvent(eventReader);
          if (event == null) {
            logger.log(
                SentryLevel.ERROR,
                "Item %d of type %s returned null by the parser.",
                items,
                item.getHeader().getType());
          } else {
            if (envelope.getHeader().getEventId() != null
                && !envelope.getHeader().getEventId().equals(event.getEventId())) {
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
            if ((hint instanceof Flushable) && !((Flushable) hint).waitFlush()) {
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
            new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(item.getData()), UTF_8))) {
          final Session session = serializer.deserializeSession(reader);
          if (session == null) {
            logger.log(
                SentryLevel.ERROR,
                "Item %d of type %s returned null by the parser.",
                items,
                item.getHeader().getType());
          } else {
            // TODO: Bundle all session in a single envelope
            hub.captureEnvelope(SentryEnvelope.fromSession(serializer, session), hint);
            logger.log(SentryLevel.DEBUG, "Item %d is being captured.", items);
            if ((hint instanceof Flushable) && !((Flushable) hint).waitFlush()) {
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

      if ((hint instanceof SubmissionResult) && !((SubmissionResult) hint).isSuccess()) {
        // Failed to send an item of the envelope: Stop attempting to send the rest (an attachment
        // without the event that created it isn't useful)
        logger.log(
            SentryLevel.WARNING,
            "Envelope had a failed capture at item %d. No more items will be sent.",
            items);
        break;
      }
    }
  }
}
