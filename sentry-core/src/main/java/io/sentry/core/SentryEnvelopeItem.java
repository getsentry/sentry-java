package io.sentry.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.concurrent.Callable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SentryEnvelopeItem {

  @SuppressWarnings("CharsetObjectCanBeUsed")
  private static final Charset UTF8 = Charset.forName("UTF-8");

  private final SentryEnvelopeItemHeader header;
  // Either dataFactory is set or data needs to be set.
  private final @Nullable Callable<byte[]> dataFactory;
  // TODO: Can we have a slice or a reader here instead?
  private @Nullable byte[] data;

  SentryEnvelopeItem(SentryEnvelopeItemHeader header, byte[] data) {
    this.header = header;
    this.data = data;
    this.dataFactory = null;
  }

  SentryEnvelopeItem(SentryEnvelopeItemHeader header, @Nullable Callable<byte[]> dataFactory) {
    this.header = header;
    this.dataFactory = dataFactory;
    this.data = null;
  }

  // TODO: Should be a Stream
  public byte[] getData() throws Exception {
    if (data == null && dataFactory != null) {
      data = dataFactory.call();
    }
    return data;
  }

  public SentryEnvelopeItemHeader getHeader() {
    return header;
  }

  public static SentryEnvelopeItem fromSession(ISerializer serializer, Session session)
      throws IOException {
    CachedItem cachedItem =
        new CachedItem(
            () -> {
              try (ByteArrayOutputStream stream = new ByteArrayOutputStream();
                  Writer writer = new OutputStreamWriter(stream, UTF8)) {
                serializer.serialize(session, writer);
                stream.flush();
                return stream.toByteArray();
              }
            });

    SentryEnvelopeItemHeader itemHeader =
        new SentryEnvelopeItemHeader(
            "session", () -> cachedItem.getBytes().length, "application/json", null);

    return new SentryEnvelopeItem(itemHeader, () -> cachedItem.getBytes());
  }

  private static class CachedItem {
    private byte[] bytes;
    private Callable<byte[]> dataFactory;

    public CachedItem(Callable<byte[]> dataFactory) {
      this.dataFactory = dataFactory;
    }

    public byte[] getBytes() throws Exception {
      if (bytes == null) {
        bytes = dataFactory.call();
      }
      return bytes;
    }
  }
}
