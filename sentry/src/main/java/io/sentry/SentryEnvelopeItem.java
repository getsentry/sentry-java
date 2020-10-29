package io.sentry;

import io.sentry.util.Objects;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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

  public @Nullable SentryEvent getEvent(final @NotNull ISerializer serializer) throws Exception {
    if (header == null || header.getType() != SentryItemType.Event) {
      return null;
    }
    try (final Reader eventReader =
        new BufferedReader(new InputStreamReader(new ByteArrayInputStream(getData()), UTF_8))) {
      return serializer.deserializeEvent(eventReader);
    }
  }

  public @Nullable Transaction getTransaction(final @NotNull ISerializer serializer)
      throws Exception {
    if (header == null || header.getType() != SentryItemType.Transaction) {
      return null;
    }
    try (final Reader eventReader =
        new BufferedReader(new InputStreamReader(new ByteArrayInputStream(getData()), UTF_8))) {
      return serializer.deserializeTransaction(eventReader);
    }
  }

  public static @NotNull SentryEnvelopeItem from(
      final @NotNull ISerializer serializer, final @NotNull Object item) throws IOException {
    Objects.requireNonNull(item, "SentryEvent is required.");

    final CachedItem cachedItem =
        new CachedItem(
            () -> {
              try (final ByteArrayOutputStream stream = new ByteArrayOutputStream();
                  final Writer writer = new BufferedWriter(new OutputStreamWriter(stream, UTF_8))) {
                serializer.serialize(item, writer);
                return stream.toByteArray();
              }
            });

    SentryEnvelopeItemHeader itemHeader =
        new SentryEnvelopeItemHeader(
            SentryItemType.resolve(item),
            () -> cachedItem.getBytes().length,
            "application/json",
            null);

    return new SentryEnvelopeItem(itemHeader, () -> cachedItem.getBytes());
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
        SentryItemType.UserFeedback, () -> cachedItem.getBytes().length, "application/json", null);

    return new SentryEnvelopeItem(itemHeader, cachedItem::getBytes);
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
