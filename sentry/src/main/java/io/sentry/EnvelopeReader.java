package io.sentry;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class EnvelopeReader implements IEnvelopeReader {

  @SuppressWarnings("CharsetObjectCanBeUsed")
  private static final Charset UTF_8 = Charset.forName("UTF-8");

  private final @NotNull ISerializer serializer;

  public EnvelopeReader(@NotNull ISerializer serializer) {
    this.serializer = serializer;
  }

  public @Override @Nullable SentryEnvelope read(final @NotNull InputStream stream)
      throws IOException {
    byte[] buffer = new byte[1024];
    int currentLength;
    int streamOffset = 0;
    // Offset of the line break defining the end of the envelope header
    int envelopeEndHeaderOffset = -1;
    try (final ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      while ((currentLength = stream.read(buffer)) > 0) {
        for (int i = 0; envelopeEndHeaderOffset == -1 && i < currentLength; i++) {
          if (buffer[i] == '\n') {
            envelopeEndHeaderOffset = streamOffset + i;
            break;
          }
        }
        outputStream.write(buffer, 0, currentLength);
        streamOffset += currentLength;
      }
      // TODO: Work on the stream instead reading to the whole thing and allocating this array
      byte[] envelopeBytes = outputStream.toByteArray();

      if (envelopeBytes.length == 0) {
        throw new IllegalArgumentException("Empty stream.");
      }
      if (envelopeEndHeaderOffset == -1) {
        throw new IllegalArgumentException("Envelope contains no header.");
      }

      SentryEnvelopeHeader header =
          deserializeEnvelopeHeader(envelopeBytes, 0, envelopeEndHeaderOffset);
      if (header == null) {
        throw new IllegalArgumentException("Envelope header is null.");
      }

      int itemHeaderStartOffset = envelopeEndHeaderOffset + 1;

      int payloadEndOffsetExclusive;
      List<SentryEnvelopeItem> items = new ArrayList<>();
      do {
        int lineBreakIndex = -1;
        // Look from startHeaderOffset until line break to find next header
        for (int i = itemHeaderStartOffset; i < envelopeBytes.length; i++) {
          if (envelopeBytes[i] == '\n') {
            lineBreakIndex = i;
            break;
          }
        }

        if (lineBreakIndex == -1) {
          throw new IllegalArgumentException(
              "Invalid envelope. Item at index '"
                  + items.size()
                  + "'. "
                  + "has no header delimiter.");
        }

        SentryEnvelopeItemHeader itemHeader =
            deserializeEnvelopeItemHeader(
                envelopeBytes, itemHeaderStartOffset, lineBreakIndex - itemHeaderStartOffset);

        if (itemHeader == null || itemHeader.getLength() <= 0) {
          throw new IllegalArgumentException(
              "Item header at index '" + items.size() + "' is null or empty.");
        }

        payloadEndOffsetExclusive = lineBreakIndex + itemHeader.getLength() + 1;
        if (payloadEndOffsetExclusive > envelopeBytes.length) {
          throw new IllegalArgumentException(
              "Invalid length for item at index '"
                  + items.size()
                  + "'. "
                  + "Item is '"
                  + payloadEndOffsetExclusive
                  + "' bytes. There are '"
                  + envelopeBytes.length
                  + "' in the buffer.");
        }

        // if 'to' parameter overflows, copyOfRange is happy to pretend nothing happened. Bound need
        // checking.
        byte[] envelopeItemBytes =
            Arrays.copyOfRange(
                envelopeBytes, lineBreakIndex + 1, payloadEndOffsetExclusive /* to is exclusive */);

        SentryEnvelopeItem item = new SentryEnvelopeItem(itemHeader, envelopeItemBytes);
        items.add(item);

        if (payloadEndOffsetExclusive == envelopeBytes.length) {
          // End of envelope
          break;
        } else if (payloadEndOffsetExclusive + 1 == envelopeBytes.length) {
          // Envelope items can be closed with a final line break
          if (envelopeBytes[payloadEndOffsetExclusive] == '\n') {
            break;
          } else {
            throw new IllegalArgumentException("Envelope has invalid data following an item.");
          }
        }

        itemHeaderStartOffset = payloadEndOffsetExclusive + 1; // Skip over delimiter
      } while (true);

      return new SentryEnvelope(header, items);
    }
  }

  private @Nullable SentryEnvelopeHeader deserializeEnvelopeHeader(
      final @NotNull byte[] buffer, int offset, int length) {
    String json = new String(buffer, offset, length, UTF_8);
    try (StringReader reader = new StringReader(json)) {
      return serializer.deserialize(reader, SentryEnvelopeHeader.class);
    }
  }

  private @Nullable SentryEnvelopeItemHeader deserializeEnvelopeItemHeader(
      final @NotNull byte[] buffer, int offset, int length) {
    String json = new String(buffer, offset, length, UTF_8);
    try (StringReader reader = new StringReader(json)) {
      return serializer.deserialize(reader, SentryEnvelopeItemHeader.class);
    }
  }
}
