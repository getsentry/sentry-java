package io.sentry.android.core;

import static io.sentry.cache.EnvelopeCache.PREFIX_CURRENT_SESSION_FILE;
import static io.sentry.cache.EnvelopeCache.PREFIX_PREVIOUS_SESSION_FILE;
import static io.sentry.cache.EnvelopeCache.STARTUP_CRASH_MARKER_FILE;
import static java.nio.charset.StandardCharsets.UTF_8;

import io.sentry.JsonObjectReader;
import io.sentry.SentryEnvelope;
import io.sentry.SentryEnvelopeItem;
import io.sentry.SentryEvent;
import io.sentry.SentryItemType;
import io.sentry.SentryLevel;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Collects native crash events from the outbox directory. These events can be correlated with
 * tombstone events from ApplicationExitInfo to avoid sending duplicate crash reports.
 */
@ApiStatus.Internal
public final class NativeEventCollector {

  private static final String NATIVE_PLATFORM = "native";

  private static final long TIMESTAMP_TOLERANCE_MS = 5000;

  private final @NotNull SentryAndroidOptions options;

  /** Lightweight metadata collected during scan phase. */
  private final @NotNull List<NativeEnvelopeMetadata> nativeEnvelopes = new ArrayList<>();

  private boolean collected = false;

  public NativeEventCollector(final @NotNull SentryAndroidOptions options) {
    this.options = options;
  }

  /** Lightweight metadata for matching phase - only file reference and timestamp. */
  static final class NativeEnvelopeMetadata {
    private final @NotNull File file;
    private final long timestampMs;

    NativeEnvelopeMetadata(final @NotNull File file, final long timestampMs) {
      this.file = file;
      this.timestampMs = timestampMs;
    }

    @NotNull
    File getFile() {
      return file;
    }

    long getTimestampMs() {
      return timestampMs;
    }
  }

  /** Holds a native event along with its source file for later deletion. */
  public static final class NativeEventData {
    private final @NotNull SentryEvent event;
    private final @NotNull File file;
    private final @NotNull SentryEnvelope envelope;

    NativeEventData(
        final @NotNull SentryEvent event,
        final @NotNull File file,
        final @NotNull SentryEnvelope envelope) {
      this.event = event;
      this.file = file;
      this.envelope = envelope;
    }

    public @NotNull SentryEvent getEvent() {
      return event;
    }

    public @NotNull File getFile() {
      return file;
    }

    public @NotNull SentryEnvelope getEnvelope() {
      return envelope;
    }
  }

  /**
   * Scans the outbox directory and collects all native crash events. This method should be called
   * once before processing tombstones. Subsequent calls are no-ops.
   */
  public void collect() {
    if (collected) {
      return;
    }
    collected = true;

    final @Nullable String outboxPath = options.getOutboxPath();
    if (outboxPath == null) {
      options
          .getLogger()
          .log(SentryLevel.DEBUG, "Outbox path is null, skipping native event collection.");
      return;
    }

    final File outboxDir = new File(outboxPath);
    if (!outboxDir.isDirectory()) {
      options.getLogger().log(SentryLevel.DEBUG, "Outbox path is not a directory: %s", outboxPath);
      return;
    }

    final File[] files = outboxDir.listFiles((d, name) -> isRelevantFileName(name));
    if (files == null || files.length == 0) {
      options.getLogger().log(SentryLevel.DEBUG, "No envelope files found in outbox.");
      return;
    }

    options
        .getLogger()
        .log(SentryLevel.DEBUG, "Scanning %d files in outbox for native events.", files.length);

    for (final File file : files) {
      if (!file.isFile()) {
        continue;
      }

      final @Nullable NativeEnvelopeMetadata metadata = extractNativeEnvelopeMetadata(file);
      if (metadata != null) {
        nativeEnvelopes.add(metadata);
        options
            .getLogger()
            .log(
                SentryLevel.DEBUG,
                "Found native event in outbox: %s (timestamp: %d)",
                file.getName(),
                metadata.getTimestampMs());
      }
    }

    options
        .getLogger()
        .log(SentryLevel.DEBUG, "Collected %d native events from outbox.", nativeEnvelopes.size());
  }

  /**
   * Finds a native event that matches the given tombstone timestamp. If a match is found, it is
   * removed from the internal list so it won't be matched again.
   *
   * <p>This method will lazily collect native events from the outbox on first call.
   *
   * @param tombstoneTimestampMs the timestamp from ApplicationExitInfo
   * @return the matching native event data, or null if no match found
   */
  public @Nullable NativeEventData findAndRemoveMatchingNativeEvent(
      final long tombstoneTimestampMs) {

    // Lazily collect on first use (runs on executor thread, not main thread)
    collect();

    for (final NativeEnvelopeMetadata metadata : nativeEnvelopes) {
      final long timeDiff = Math.abs(tombstoneTimestampMs - metadata.getTimestampMs());
      if (timeDiff <= TIMESTAMP_TOLERANCE_MS) {
        options
            .getLogger()
            .log(SentryLevel.DEBUG, "Matched native event by timestamp (diff: %d ms)", timeDiff);
        nativeEnvelopes.remove(metadata);
        // Only load full event data when we have a match
        return loadFullNativeEventData(metadata.getFile());
      }
    }

    return null;
  }

  /**
   * Deletes a native event file from the outbox.
   *
   * @param nativeEventData the native event data containing the file reference
   * @return true if the file was deleted successfully
   */
  public boolean deleteNativeEventFile(final @NotNull NativeEventData nativeEventData) {
    final File file = nativeEventData.getFile();
    try {
      if (file.delete()) {
        options
            .getLogger()
            .log(SentryLevel.DEBUG, "Deleted native event file from outbox: %s", file.getName());
        return true;
      } else {
        options
            .getLogger()
            .log(
                SentryLevel.WARNING,
                "Failed to delete native event file: %s",
                file.getAbsolutePath());
        return false;
      }
    } catch (Throwable e) {
      options
          .getLogger()
          .log(
              SentryLevel.ERROR, e, "Error deleting native event file: %s", file.getAbsolutePath());
      return false;
    }
  }

  /**
   * Extracts only lightweight metadata (timestamp) from an envelope file using streaming parsing.
   * This avoids loading the entire envelope and deserializing the full event.
   */
  private @Nullable NativeEnvelopeMetadata extractNativeEnvelopeMetadata(final @NotNull File file) {
    // we use the backend envelope size limit as a bound for the read loop
    final long maxEnvelopeSize = 200 * 1024 * 1024;
    long bytesProcessed = 0;

    try (final InputStream stream = new BufferedInputStream(new FileInputStream(file))) {
      // Skip envelope header line
      final int headerBytes = skipLine(stream);
      if (headerBytes < 0) {
        return null;
      }
      bytesProcessed += headerBytes;

      while (bytesProcessed < maxEnvelopeSize) {
        final @Nullable String itemHeaderLine = readLine(stream);
        if (itemHeaderLine == null || itemHeaderLine.isEmpty()) {
          // We reached the end of the envelope
          break;
        }
        bytesProcessed += itemHeaderLine.length() + 1; // +1 for newline

        final @Nullable ItemHeaderInfo headerInfo = parseItemHeader(itemHeaderLine);
        if (headerInfo == null) {
          break;
        }

        if ("event".equals(headerInfo.type)) {
          final @Nullable NativeEnvelopeMetadata metadata =
              extractMetadataFromEventPayload(stream, headerInfo.length, file);
          if (metadata != null) {
            return metadata;
          }
        } else {
          skipBytes(stream, headerInfo.length);
        }
        bytesProcessed += headerInfo.length;

        // Skip the newline after payload (if present)
        final int next = stream.read();
        if (next == -1) {
          break;
        }
        bytesProcessed++;
        if (next != '\n') {
          // Not a newline, we're at the next item header. Can't unread easily,
          // but this shouldn't happen with well-formed envelopes
          break;
        }
      }
    } catch (Throwable e) {
      options
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              e,
              "Error extracting metadata from envelope file: %s",
              file.getAbsolutePath());
    }
    return null;
  }

  /**
   * Extracts platform and timestamp from an event payload using streaming JSON parsing. Only reads
   * the fields we need and exits early once found. Uses a bounded stream to track position within
   * the payload and skip any unread bytes on close, avoiding allocation of the full payload.
   */
  private @Nullable NativeEnvelopeMetadata extractMetadataFromEventPayload(
      final @NotNull InputStream stream, final int payloadLength, final @NotNull File file) {

    NativeEnvelopeMetadata result = null;

    try (final BoundedInputStream boundedStream = new BoundedInputStream(stream, payloadLength);
        final Reader reader = new InputStreamReader(boundedStream, UTF_8)) {
      final JsonObjectReader jsonReader = new JsonObjectReader(reader);

      String platform = null;
      Date timestamp = null;

      jsonReader.beginObject();
      while (jsonReader.peek() == JsonToken.NAME) {
        final String name = jsonReader.nextName();
        switch (name) {
          case "platform":
            platform = jsonReader.nextStringOrNull();
            break;
          case "timestamp":
            timestamp = jsonReader.nextDateOrNull(options.getLogger());
            break;
          default:
            jsonReader.skipValue();
            break;
        }
        if (platform != null && timestamp != null) {
          break;
        }
      }

      if (NATIVE_PLATFORM.equals(platform) && timestamp != null) {
        result = new NativeEnvelopeMetadata(file, timestamp.getTime());
      }
    } catch (Throwable e) {
      options
          .getLogger()
          .log(SentryLevel.DEBUG, e, "Error parsing event JSON from: %s", file.getName());
    }

    return result;
  }

  /** Loads the full envelope and event data from a file. Used only when a match is found. */
  private @Nullable NativeEventData loadFullNativeEventData(final @NotNull File file) {
    try (final InputStream stream = new BufferedInputStream(new FileInputStream(file))) {
      final SentryEnvelope envelope = options.getEnvelopeReader().read(stream);
      if (envelope == null) {
        return null;
      }

      for (final SentryEnvelopeItem item : envelope.getItems()) {
        if (!SentryItemType.Event.equals(item.getHeader().getType())) {
          continue;
        }

        try (final Reader eventReader =
            new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(item.getData()), UTF_8))) {
          final SentryEvent event =
              options.getSerializer().deserialize(eventReader, SentryEvent.class);
          if (event != null && NATIVE_PLATFORM.equals(event.getPlatform())) {
            return new NativeEventData(event, file, envelope);
          }
        }
      }
    } catch (Throwable e) {
      options
          .getLogger()
          .log(SentryLevel.DEBUG, e, "Error loading envelope file: %s", file.getAbsolutePath());
    }
    return null;
  }

  /** Minimal item header info needed for streaming. */
  private static final class ItemHeaderInfo {
    final @Nullable String type;
    final int length;

    ItemHeaderInfo(final @Nullable String type, final int length) {
      this.type = type;
      this.length = length;
    }
  }

  /** Parses item header JSON to extract only type and length fields. */
  private @Nullable ItemHeaderInfo parseItemHeader(final @NotNull String headerLine) {
    try (final Reader reader =
        new InputStreamReader(new ByteArrayInputStream(headerLine.getBytes(UTF_8)), UTF_8)) {
      final JsonObjectReader jsonReader = new JsonObjectReader(reader);

      String type = null;
      int length = -1;

      jsonReader.beginObject();
      while (jsonReader.peek() == JsonToken.NAME) {
        final String name = jsonReader.nextName();
        switch (name) {
          case "type":
            type = jsonReader.nextStringOrNull();
            break;
          case "length":
            length = jsonReader.nextInt();
            break;
          default:
            jsonReader.skipValue();
            break;
        }
        // Early exit if we have both
        if (type != null && length >= 0) {
          break;
        }
      }

      if (length >= 0) {
        return new ItemHeaderInfo(type, length);
      }
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.DEBUG, e, "Error parsing item header");
    }
    return null;
  }

  /** Reads a line from the stream (up to and including newline). Returns null on EOF. */
  private @Nullable String readLine(final @NotNull InputStream stream) throws IOException {
    final StringBuilder sb = new StringBuilder();
    int b;
    while ((b = stream.read()) != -1) {
      if (b == '\n') {
        return sb.toString();
      }
      sb.append((char) b);
    }
    return sb.length() > 0 ? sb.toString() : null;
  }

  /**
   * Skips a line in the stream (up to and including newline). Returns bytes skipped, or -1 on EOF.
   */
  private int skipLine(final @NotNull InputStream stream) throws IOException {
    int count = 0;
    int b;
    while ((b = stream.read()) != -1) {
      count++;
      if (b == '\n') {
        return count;
      }
    }
    return count > 0 ? count : -1;
  }

  /** Skips exactly n bytes from the stream. */
  private static void skipBytes(final @NotNull InputStream stream, final long count)
      throws IOException {
    long remaining = count;
    while (remaining > 0) {
      final long skipped = stream.skip(remaining);
      if (skipped == 0) {
        // skip() returned 0, try reading instead
        if (stream.read() == -1) {
          throw new EOFException("Unexpected end of stream while skipping bytes");
        }
        remaining--;
      } else {
        remaining -= skipped;
      }
    }
  }

  private boolean isRelevantFileName(final @Nullable String fileName) {
    return fileName != null
        && !fileName.startsWith(PREFIX_CURRENT_SESSION_FILE)
        && !fileName.startsWith(PREFIX_PREVIOUS_SESSION_FILE)
        && !fileName.startsWith(STARTUP_CRASH_MARKER_FILE);
  }

  /**
   * An InputStream wrapper that tracks reads within a bounded section of the stream. This allows
   * callers to read/parse only what they need (e.g., extract a few JSON fields), then skip the
   * remainder of the section on close to position the stream at the next envelope item. Does not
   * close the underlying stream.
   */
  private static final class BoundedInputStream extends InputStream {
    private final @NotNull InputStream inner;
    private long remaining;

    BoundedInputStream(final @NotNull InputStream inner, final int limit) {
      this.inner = inner;
      this.remaining = limit;
    }

    @Override
    public int read() throws IOException {
      if (remaining <= 0) {
        return -1;
      }
      final int result = inner.read();
      if (result != -1) {
        remaining--;
      }
      return result;
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
      if (remaining <= 0) {
        return -1;
      }
      final int toRead = Math.min(len, (int) remaining);
      final int result = inner.read(b, off, toRead);
      if (result > 0) {
        remaining -= result;
      }
      return result;
    }

    @Override
    public long skip(final long n) throws IOException {
      final long toSkip = Math.min(n, remaining);
      final long skipped = inner.skip(toSkip);
      remaining -= skipped;
      return skipped;
    }

    @Override
    public int available() throws IOException {
      return Math.min(inner.available(), (int) remaining);
    }

    @Override
    public void close() throws IOException {
      // Skip any remaining bytes to advance the underlying stream position,
      // but don't close the underlying stream, because we might have other
      // envelope items to read.
      skipBytes(inner, remaining);
    }
  }
}
