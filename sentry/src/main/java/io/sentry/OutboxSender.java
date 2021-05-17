package io.sentry;

import static io.sentry.SentryLevel.ERROR;
import static io.sentry.cache.EnvelopeCache.PREFIX_CURRENT_SESSION_FILE;

import io.sentry.hints.Flushable;
import io.sentry.hints.Resettable;
import io.sentry.hints.Retryable;
import io.sentry.hints.SubmissionResult;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.SentryTransaction;
import io.sentry.util.CollectionUtils;
import io.sentry.util.LogUtils;
import io.sentry.util.Objects;
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
public final class OutboxSender extends DirectoryProcessor implements IEnvelopeSender {

  @SuppressWarnings("CharsetObjectCanBeUsed")
  private static final Charset UTF_8 = Charset.forName("UTF-8");

  private final @NotNull IHub hub;
  private final @NotNull IEnvelopeReader envelopeReader;
  private final @NotNull ISerializer serializer;
  private final @NotNull ILogger logger;

  public OutboxSender(
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
      logger.log(SentryLevel.DEBUG, "File '%s' should be ignored.", file.getAbsolutePath());
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
        logger.log(SentryLevel.DEBUG, "File '%s' is done.", file.getAbsolutePath());
      }
    } catch (IOException e) {
      logger.log(SentryLevel.ERROR, "Error processing envelope.", e);
    } finally {
      if (hint instanceof Retryable) {
        if (!((Retryable) hint).isRetry()) {
          try {
            if (!file.delete()) {
              logger.log(SentryLevel.ERROR, "Failed to delete: %s", file.getAbsolutePath());
            }
          } catch (RuntimeException e) {
            logger.log(SentryLevel.ERROR, e, "Failed to delete: %s", file.getAbsolutePath());
          }
        }
      } else {
        LogUtils.logIfNotRetryable(logger, hint);
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
    int currentItem = 0;

    for (final SentryEnvelopeItem item : envelope.getItems()) {
      currentItem++;

      if (item.getHeader() == null) {
        logger.log(SentryLevel.ERROR, "Item %d has no header", currentItem);
        continue;
      }
      if (SentryItemType.Event.equals(item.getHeader().getType())) {
        try (final Reader eventReader =
            new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(item.getData()), UTF_8))) {
          SentryEvent event = serializer.deserialize(eventReader, SentryEvent.class);
          if (event == null) {
            logEnvelopeItemNull(item, currentItem);
          } else {
            if (envelope.getHeader().getEventId() != null
                && !envelope.getHeader().getEventId().equals(event.getEventId())) {
              logUnexpectedEventId(envelope, event.getEventId(), currentItem);
              continue;
            }
            hub.captureEvent(event, hint);
            logItemCaptured(currentItem);

            if (!waitFlush(hint)) {
              logTimeout(event.getEventId());
              break;
            }
          }
        } catch (Exception e) {
          logger.log(ERROR, "Item failed to process.", e);
        }
      } else if (SentryItemType.Transaction.equals(item.getHeader().getType())) {
        try (final Reader reader =
            new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(item.getData()), UTF_8))) {

          final SentryTransaction transaction = serializer.deserialize(reader, SentryTransaction.class);
          if (transaction == null) {
            logEnvelopeItemNull(item, currentItem);
          } else {
            if (envelope.getHeader().getEventId() != null
                && !envelope.getHeader().getEventId().equals(transaction.getEventId())) {
              logUnexpectedEventId(envelope, transaction.getEventId(), currentItem);
              continue;
            }

            if (transaction.getContexts().getTrace() != null) {
              // Hint: Set sampled in order for the transaction not to be dropped, as this is a
              // transient property.
              transaction.getContexts().getTrace().setSampled(true);
            }
            hub.captureTransaction(transaction, hint);
            logItemCaptured(currentItem);

            if (!waitFlush(hint)) {
              logTimeout(transaction.getEventId());
              break;
            }
          }
        } catch (Exception e) {
          logger.log(ERROR, "Item failed to process.", e);
        }
      } else {
        // send unknown item types over the wire
        final SentryEnvelope newEnvelope =
            new SentryEnvelope(
                envelope.getHeader().getEventId(), envelope.getHeader().getSdkVersion(), item);
        hub.captureEnvelope(newEnvelope, hint);
        logger.log(
            SentryLevel.DEBUG,
            "%s item %d is being captured.",
            item.getHeader().getType().getItemType(),
            currentItem);

        if (!waitFlush(hint)) {
          logger.log(
              SentryLevel.WARNING,
              "Timed out waiting for item type submission: %s",
              item.getHeader().getType().getItemType());
          break;
        }
      }

      if (hint instanceof SubmissionResult) {
        if (!((SubmissionResult) hint).isSuccess()) {
          // Failed to send an item of the envelope: Stop attempting to send the rest (an attachment
          // without the event that created it isn't useful)
          logger.log(
              SentryLevel.WARNING,
              "Envelope had a failed capture at item %d. No more items will be sent.",
              currentItem);
          break;
        }
      }

      // reset the Hint to its initial state as we use it multiple times.
      if (hint instanceof Resettable) {
        ((Resettable) hint).reset();
      }
    }
  }

  private void logEnvelopeItemNull(final @NonNull SentryEnvelopeItem item, int itemIndex) {
    logger.log(
        SentryLevel.ERROR,
        "Item %d of type %s returned null by the parser.",
        itemIndex,
        item.getHeader().getType());
  }

  private void logUnexpectedEventId(final @NonNull SentryEnvelope envelope, SentryId eventId, int itemIndex) {
    logger.log(
        SentryLevel.ERROR,
        "Item %d of has a different event id (%s) to the envelope header (%s)",
        itemIndex,
        envelope.getHeader().getEventId(),
        eventId);
  }

  private void logItemCaptured(int itemIndex) {
    logger.log(SentryLevel.DEBUG, "Item %d is being captured.", itemIndex);
  }

  private void logTimeout(final @NonNull SentryId eventId) {
    logger.log(SentryLevel.WARNING, "Timed out waiting for event id submission: %s", eventId);
  }

  private boolean waitFlush(final @Nullable Object hint) {
    if (hint instanceof Flushable) {
      return ((Flushable) hint).waitFlush();
    } else {
      LogUtils.logIfNotFlushable(logger, hint);
    }
    return true;
  }
}
