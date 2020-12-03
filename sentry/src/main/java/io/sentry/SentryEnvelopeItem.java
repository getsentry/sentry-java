package io.sentry;

import io.sentry.util.Objects;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.concurrent.Callable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SentryEnvelopeItem {

  @SuppressWarnings("CharsetObjectCanBeUsed")
  private static final Charset UTF_8 = Charset.forName("UTF-8");

  private final SentryEnvelopeItemHeader header;
  // Either dataFactory is set or data needs to be set.
  private final @Nullable Callable<byte[]> dataFactory;
  // TODO: Can we have a slice or a reader here instead?
  private @Nullable byte[] data;

  SentryEnvelopeItem(final @NotNull SentryEnvelopeItemHeader header, final byte[] data) {
    this.header = Objects.requireNonNull(header, "SentryEnvelopeItemHeader is required.");
    this.data = data;
    this.dataFactory = null;
  }

  SentryEnvelopeItem(
      final @NotNull SentryEnvelopeItemHeader header,
      final @Nullable Callable<byte[]> dataFactory) {
    this.header = Objects.requireNonNull(header, "SentryEnvelopeItemHeader is required.");
    this.dataFactory = Objects.requireNonNull(dataFactory, "DataFactory is required.");
    this.data = null;
  }

  // TODO: Should be a Stream
  public @NotNull byte[] getData() throws Exception {
    if (data == null && dataFactory != null) {
      data = dataFactory.call();
    }
    return data;
  }

  public @NotNull SentryEnvelopeItemHeader getHeader() {
    return header;
  }

  public static @NotNull SentryEnvelopeItem fromSession(
      final @NotNull ISerializer serializer, final @NotNull Session session) throws IOException {
    Objects.requireNonNull(serializer, "ISerializer is required.");
    Objects.requireNonNull(session, "Session is required.");

    final CachedItem cachedItem =
        new CachedItem(
            () -> {
              try (final ByteArrayOutputStream stream = new ByteArrayOutputStream();
                  final Writer writer = new BufferedWriter(new OutputStreamWriter(stream, UTF_8))) {
                serializer.serialize(session, writer);
                return stream.toByteArray();
              }
            });

    SentryEnvelopeItemHeader itemHeader =
        new SentryEnvelopeItemHeader(
            SentryItemType.Session, () -> cachedItem.getBytes().length, "application/json", null);

    return new SentryEnvelopeItem(itemHeader, () -> cachedItem.getBytes());
  }

  public @Nullable SentryEvent getEvent(final @NotNull ISerializer serializer) throws Exception {
    if (header == null || header.getType() != SentryItemType.Event) {
      return null;
    }
    try (final Reader eventReader =
        new BufferedReader(new InputStreamReader(new ByteArrayInputStream(getData()), UTF_8))) {
      return serializer.deserialize(eventReader, SentryEvent.class);
    }
  }

  public static @NotNull SentryEnvelopeItem fromEvent(
      final @NotNull ISerializer serializer, final @NotNull SentryBaseEvent event)
      throws IOException {
    Objects.requireNonNull(serializer, "ISerializer is required.");
    Objects.requireNonNull(event, "SentryEvent is required.");

    final CachedItem cachedItem =
        new CachedItem(
            () -> {
              try (final ByteArrayOutputStream stream = new ByteArrayOutputStream();
                  final Writer writer = new BufferedWriter(new OutputStreamWriter(stream, UTF_8))) {
                serializer.serialize(event, writer);
                return stream.toByteArray();
              }
            });

    SentryEnvelopeItemHeader itemHeader =
        new SentryEnvelopeItemHeader(
            SentryItemType.resolve(event),
            () -> cachedItem.getBytes().length,
            "application/json",
            null);

    return new SentryEnvelopeItem(itemHeader, () -> cachedItem.getBytes());
  }

  public @Nullable SentryTransaction getTransaction(final @NotNull ISerializer serializer)
      throws Exception {
    if (header == null || header.getType() != SentryItemType.Transaction) {
      return null;
    }
    try (final Reader eventReader =
        new BufferedReader(new InputStreamReader(new ByteArrayInputStream(getData()), UTF_8))) {
      return serializer.deserialize(eventReader, SentryTransaction.class);
    }
  }

  public static SentryEnvelopeItem fromUserFeedback(
      final @NotNull ISerializer serializer, final @NotNull UserFeedback userFeedback) {
    Objects.requireNonNull(serializer, "ISerializer is required.");
    Objects.requireNonNull(userFeedback, "UserFeedback is required.");

    final CachedItem cachedItem =
        new CachedItem(
            () -> {
              try (final ByteArrayOutputStream stream = new ByteArrayOutputStream();
                  final Writer writer = new BufferedWriter(new OutputStreamWriter(stream, UTF_8))) {
                serializer.serialize(userFeedback, writer);
                return stream.toByteArray();
              }
            });

    SentryEnvelopeItemHeader itemHeader =
        new SentryEnvelopeItemHeader(
            SentryItemType.UserFeedback,
            () -> cachedItem.getBytes().length,
            "application/json",
            null);

    return new SentryEnvelopeItem(itemHeader, cachedItem::getBytes);
  }

  public static SentryEnvelopeItem fromAttachment(
      @NotNull ILogger logger, final @NotNull Attachment attachment) {

    final CachedItem cachedItem =
        new CachedItem(
            () -> {
              if (attachment.getBytes() != null) {
                return attachment.getBytes();
              } else if (attachment.getPathname() != null) {
                try (FileInputStream fileInputStream =
                        new FileInputStream(attachment.getPathname());
                    BufferedInputStream inputStream = new BufferedInputStream(fileInputStream);
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

                  byte[] bytes = new byte[1024];
                  int length;
                  int offset = 0;
                  while ((length = inputStream.read(bytes)) != -1) {
                    outputStream.write(bytes, offset, length);
                  }
                  return outputStream.toByteArray();
                } catch (IOException | SecurityException exception) {
                  logger.log(
                      SentryLevel.ERROR,
                      exception,
                      "Serializing attachment %s failed.",
                      attachment.getPathname());
                }
              }

              return new byte[0];
            });

    SentryEnvelopeItemHeader itemHeader =
        new SentryEnvelopeItemHeader(
            SentryItemType.Attachment,
            () -> cachedItem.getBytes().length,
            attachment.getContentType(),
            attachment.getFilename());

    // Don't use method reference. This can cause issues on Android
    return new SentryEnvelopeItem(itemHeader, () -> cachedItem.getBytes());
  }

  private static class CachedItem {
    private @Nullable byte[] bytes;
    private final @Nullable Callable<byte[]> dataFactory;

    public CachedItem(final @Nullable Callable<byte[]> dataFactory) {
      this.dataFactory = dataFactory;
    }

    public @Nullable byte[] getBytes() throws Exception {
      if (bytes == null) {
        bytes = dataFactory.call();
      }
      return bytes;
    }
  }
}
