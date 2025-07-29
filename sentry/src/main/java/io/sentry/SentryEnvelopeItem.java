package io.sentry;

import static io.sentry.util.FileUtils.readBytesFromFile;
import static io.sentry.vendor.Base64.NO_PADDING;
import static io.sentry.vendor.Base64.NO_WRAP;

import io.sentry.clientreport.ClientReport;
import io.sentry.exception.SentryEnvelopeException;
import io.sentry.profiling.ProfilingServiceLoader;
import io.sentry.protocol.SentryTransaction;
import io.sentry.protocol.profiling.SentryProfile;
import io.sentry.util.FileUtils;
import io.sentry.util.JsonSerializationUtils;
import io.sentry.util.Objects;
import io.sentry.vendor.Base64;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SentryEnvelopeItem {

  // Profiles bigger than 50 MB will be dropped by the backend, so we drop bigger ones
  private static final long MAX_PROFILE_CHUNK_SIZE = 50 * 1024 * 1024; // 50MB

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
  // dataFactory is a Callable which returns theoretically a nullable result. Our implementations
  // always provide non-null values.
  @SuppressWarnings("NullAway")
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

    // avoid method refs on Android due to some issues with older AGP setups
    // noinspection Convert2MethodRef
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
      final @NotNull ISerializer serializer, final @NotNull SentryBaseEvent event) {
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

    // avoid method refs on Android due to some issues with older AGP setups
    // noinspection Convert2MethodRef
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

  public @Nullable SentryLogEvents getLogs(final @NotNull ISerializer serializer) throws Exception {
    if (header == null || header.getType() != SentryItemType.Log) {
      return null;
    }
    try (final Reader eventReader =
        new BufferedReader(new InputStreamReader(new ByteArrayInputStream(getData()), UTF_8))) {
      return serializer.deserialize(eventReader, SentryLogEvents.class);
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

    // avoid method refs on Android due to some issues with older AGP setups
    // noinspection Convert2MethodRef
    return new SentryEnvelopeItem(itemHeader, () -> cachedItem.getBytes());
  }

  public static SentryEnvelopeItem fromCheckIn(
      final @NotNull ISerializer serializer, final @NotNull CheckIn checkIn) {
    Objects.requireNonNull(serializer, "ISerializer is required.");
    Objects.requireNonNull(checkIn, "CheckIn is required.");

    final CachedItem cachedItem =
        new CachedItem(
            () -> {
              try (final ByteArrayOutputStream stream = new ByteArrayOutputStream();
                  final Writer writer = new BufferedWriter(new OutputStreamWriter(stream, UTF_8))) {
                serializer.serialize(checkIn, writer);
                return stream.toByteArray();
              }
            });

    SentryEnvelopeItemHeader itemHeader =
        new SentryEnvelopeItemHeader(
            SentryItemType.CheckIn, () -> cachedItem.getBytes().length, "application/json", null);

    // avoid method refs on Android due to some issues with older AGP setups
    // noinspection Convert2MethodRef
    return new SentryEnvelopeItem(itemHeader, () -> cachedItem.getBytes());
  }

  public static SentryEnvelopeItem fromAttachment(
      final @NotNull ISerializer serializer,
      final @NotNull ILogger logger,
      final @NotNull Attachment attachment,
      final long maxAttachmentSize) {

    final CachedItem cachedItem =
        new CachedItem(
            () -> {
              if (attachment.getBytes() != null) {
                final byte[] data = attachment.getBytes();
                ensureAttachmentSizeLimit(data.length, maxAttachmentSize, attachment.getFilename());
                return data;
              } else if (attachment.getSerializable() != null) {
                final JsonSerializable serializable = attachment.getSerializable();
                @SuppressWarnings("NullableProblems")
                final @Nullable byte[] data =
                    JsonSerializationUtils.bytesFrom(serializer, logger, serializable);

                if (data != null) {
                  ensureAttachmentSizeLimit(
                      data.length, maxAttachmentSize, attachment.getFilename());
                  return data;
                }
              } else if (attachment.getPathname() != null) {
                return readBytesFromFile(attachment.getPathname(), maxAttachmentSize);
              } else if (attachment.getByteProvider() != null) {
                @SuppressWarnings("NullableProblems")
                final @Nullable byte[] data = attachment.getByteProvider().call();
                if (data != null) {
                  ensureAttachmentSizeLimit(
                      data.length, maxAttachmentSize, attachment.getFilename());
                  return data;
                }
              }
              throw new SentryEnvelopeException(
                  String.format(
                      "Couldn't attach the attachment %s.\n"
                          + "Please check that either bytes, serializable, path or provider is set.",
                      attachment.getFilename()));
            });

    SentryEnvelopeItemHeader itemHeader =
        new SentryEnvelopeItemHeader(
            SentryItemType.Attachment,
            () -> cachedItem.getBytes().length,
            attachment.getContentType(),
            attachment.getFilename(),
            attachment.getAttachmentType());

    // avoid method refs on Android due to some issues with older AGP setups
    // noinspection Convert2MethodRef
    return new SentryEnvelopeItem(itemHeader, () -> cachedItem.getBytes());
  }

  private static void ensureAttachmentSizeLimit(
      final long size, final long maxAttachmentSize, final @NotNull String filename)
      throws SentryEnvelopeException {
    if (size > maxAttachmentSize) {
      throw new SentryEnvelopeException(
          String.format(
              "Dropping attachment with filename '%s', because the "
                  + "size of the passed bytes with %d bytes is bigger "
                  + "than the maximum allowed attachment size of "
                  + "%d bytes.",
              filename, size, maxAttachmentSize));
    }
  }

  public static @NotNull SentryEnvelopeItem fromProfileChunk(
      final @NotNull ProfileChunk profileChunk, final @NotNull ISerializer serializer)
      throws SentryEnvelopeException {

    final @NotNull File traceFile = profileChunk.getTraceFile();
    // Using CachedItem, so we read the trace file in the background
    final CachedItem cachedItem =
        new CachedItem(
            () -> {
              if (!traceFile.exists()) {
                throw new SentryEnvelopeException(
                    String.format(
                        "Dropping profile chunk, because the file '%s' doesn't exists",
                        traceFile.getName()));
              }

              if (ProfileChunk.Platform.JAVA == profileChunk.getPlatform()) {
                final IProfileConverter profileConverter =
                    ProfilingServiceLoader.loadProfileConverter();
                if (profileConverter != null) {
                  try {
                    final SentryProfile profile =
                        profileConverter.convertFromFile(traceFile.toPath());
                    profileChunk.setSentryProfile(profile);
                  } catch (IOException e) {
                    throw new SentryEnvelopeException("Profile conversion failed");
                  }
                }
              } else {
                // The payload of the profile item is a json including the trace file encoded with
                // base64
                final byte[] traceFileBytes =
                    readBytesFromFile(traceFile.getPath(), MAX_PROFILE_CHUNK_SIZE);
                final @NotNull String base64Trace =
                    Base64.encodeToString(traceFileBytes, NO_WRAP | NO_PADDING);
                if (base64Trace.isEmpty()) {
                  throw new SentryEnvelopeException("Profiling trace file is empty");
                }
                profileChunk.setSampledProfile(base64Trace);
              }

              try (final ByteArrayOutputStream stream = new ByteArrayOutputStream();
                  final Writer writer = new BufferedWriter(new OutputStreamWriter(stream, UTF_8))) {
                serializer.serialize(profileChunk, writer);
                return stream.toByteArray();
              } catch (IOException e) {
                throw new SentryEnvelopeException(
                    String.format("Failed to serialize profile chunk\n%s", e.getMessage()));
              } finally {
                // In any case we delete the trace file
                traceFile.delete();
              }
            });

    SentryEnvelopeItemHeader itemHeader =
        new SentryEnvelopeItemHeader(
            SentryItemType.ProfileChunk,
            () -> cachedItem.getBytes().length,
            "application-json",
            traceFile.getName(),
            null,
            profileChunk.getPlatform().apiName(),
            null);

    // avoid method refs on Android due to some issues with older AGP setups
    // noinspection Convert2MethodRef
    return new SentryEnvelopeItem(itemHeader, () -> cachedItem.getBytes());
  }

  public static @NotNull SentryEnvelopeItem fromProfilingTrace(
      final @NotNull ProfilingTraceData profilingTraceData,
      final long maxTraceFileSize,
      final @NotNull ISerializer serializer)
      throws SentryEnvelopeException {

    final @NotNull File traceFile = profilingTraceData.getTraceFile();
    // Using CachedItem, so we read the trace file in the background
    final CachedItem cachedItem =
        new CachedItem(
            () -> {
              if (!traceFile.exists()) {
                throw new SentryEnvelopeException(
                    String.format(
                        "Dropping profiling trace data, because the file '%s' doesn't exists",
                        traceFile.getName()));
              }
              // The payload of the profile item is a json including the trace file encoded with
              // base64
              final byte[] traceFileBytes =
                  readBytesFromFile(traceFile.getPath(), maxTraceFileSize);
              final @NotNull String base64Trace =
                  Base64.encodeToString(traceFileBytes, NO_WRAP | NO_PADDING);
              if (base64Trace.isEmpty()) {
                throw new SentryEnvelopeException("Profiling trace file is empty");
              }
              profilingTraceData.setSampledProfile(base64Trace);
              profilingTraceData.readDeviceCpuFrequencies();

              try (final ByteArrayOutputStream stream = new ByteArrayOutputStream();
                  final Writer writer = new BufferedWriter(new OutputStreamWriter(stream, UTF_8))) {
                serializer.serialize(profilingTraceData, writer);
                return stream.toByteArray();
              } catch (IOException e) {
                throw new SentryEnvelopeException(
                    String.format("Failed to serialize profiling trace data\n%s", e.getMessage()));
              } finally {
                // In any case we delete the trace file
                traceFile.delete();
              }
            });

    SentryEnvelopeItemHeader itemHeader =
        new SentryEnvelopeItemHeader(
            SentryItemType.Profile,
            () -> cachedItem.getBytes().length,
            "application-json",
            traceFile.getName());

    // avoid method refs on Android due to some issues with older AGP setups
    // noinspection Convert2MethodRef
    return new SentryEnvelopeItem(itemHeader, () -> cachedItem.getBytes());
  }

  public static @NotNull SentryEnvelopeItem fromClientReport(
      final @NotNull ISerializer serializer, final @NotNull ClientReport clientReport)
      throws IOException {
    Objects.requireNonNull(serializer, "ISerializer is required.");
    Objects.requireNonNull(clientReport, "ClientReport is required.");

    final CachedItem cachedItem =
        new CachedItem(
            () -> {
              try (final ByteArrayOutputStream stream = new ByteArrayOutputStream();
                  final Writer writer = new BufferedWriter(new OutputStreamWriter(stream, UTF_8))) {
                serializer.serialize(clientReport, writer);
                return stream.toByteArray();
              }
            });

    SentryEnvelopeItemHeader itemHeader =
        new SentryEnvelopeItemHeader(
            SentryItemType.resolve(clientReport),
            () -> cachedItem.getBytes().length,
            "application/json",
            null);

    // avoid method refs on Android due to some issues with older AGP setups
    // noinspection Convert2MethodRef
    return new SentryEnvelopeItem(itemHeader, () -> cachedItem.getBytes());
  }

  @Nullable
  public ClientReport getClientReport(final @NotNull ISerializer serializer) throws Exception {
    if (header == null || header.getType() != SentryItemType.ClientReport) {
      return null;
    }
    try (final Reader eventReader =
        new BufferedReader(new InputStreamReader(new ByteArrayInputStream(getData()), UTF_8))) {
      return serializer.deserialize(eventReader, ClientReport.class);
    }
  }

  public static SentryEnvelopeItem fromReplay(
      final @NotNull ISerializer serializer,
      final @NotNull ILogger logger,
      final @NotNull SentryReplayEvent replayEvent,
      final @Nullable ReplayRecording replayRecording,
      final boolean cleanupReplayFolder) {

    final File replayVideo = replayEvent.getVideoFile();

    final CachedItem cachedItem =
        new CachedItem(
            () -> {
              try {
                try (final ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    final Writer writer =
                        new BufferedWriter(new OutputStreamWriter(stream, UTF_8))) {
                  // relay expects the payload to be in this exact order: [event,rrweb,video]
                  final Map<String, byte[]> replayPayload = new LinkedHashMap<>();
                  // first serialize replay event json bytes
                  serializer.serialize(replayEvent, writer);
                  replayPayload.put(SentryItemType.ReplayEvent.getItemType(), stream.toByteArray());
                  stream.reset();

                  // next serialize replay recording
                  if (replayRecording != null) {
                    serializer.serialize(replayRecording, writer);
                    replayPayload.put(
                        SentryItemType.ReplayRecording.getItemType(), stream.toByteArray());
                    stream.reset();
                  }

                  // next serialize replay video bytes from given file
                  if (replayVideo != null && replayVideo.exists()) {
                    final byte[] videoBytes =
                        readBytesFromFile(
                            replayVideo.getPath(), SentryReplayEvent.REPLAY_VIDEO_MAX_SIZE);
                    if (videoBytes.length > 0) {
                      replayPayload.put(SentryItemType.ReplayVideo.getItemType(), videoBytes);
                    }
                  }

                  return serializeToMsgpack(replayPayload);
                }
              } catch (Throwable t) {
                logger.log(SentryLevel.ERROR, "Could not serialize replay recording", t);
                return null;
              } finally {
                if (replayVideo != null) {
                  if (cleanupReplayFolder) {
                    FileUtils.deleteRecursively(replayVideo.getParentFile());
                  } else {
                    replayVideo.delete();
                  }
                }
              }
            });

    final SentryEnvelopeItemHeader itemHeader =
        new SentryEnvelopeItemHeader(
            SentryItemType.ReplayVideo, () -> cachedItem.getBytes().length, null, null);

    // avoid method refs on Android due to some issues with older AGP setups
    // noinspection Convert2MethodRef
    return new SentryEnvelopeItem(itemHeader, () -> cachedItem.getBytes());
  }

  public static SentryEnvelopeItem fromLogs(
      final @NotNull ISerializer serializer, final @NotNull SentryLogEvents logEvents) {
    Objects.requireNonNull(serializer, "ISerializer is required.");
    Objects.requireNonNull(logEvents, "SentryLogEvents is required.");

    final CachedItem cachedItem =
        new CachedItem(
            () -> {
              try (final ByteArrayOutputStream stream = new ByteArrayOutputStream();
                  final Writer writer = new BufferedWriter(new OutputStreamWriter(stream, UTF_8))) {
                serializer.serialize(logEvents, writer);
                return stream.toByteArray();
              }
            });

    SentryEnvelopeItemHeader itemHeader =
        new SentryEnvelopeItemHeader(
            SentryItemType.Log,
            () -> cachedItem.getBytes().length,
            "application/vnd.sentry.items.log+json",
            null,
            null,
            null,
            logEvents.getItems().size());

    // avoid method refs on Android due to some issues with older AGP setups
    // noinspection Convert2MethodRef
    return new SentryEnvelopeItem(itemHeader, () -> cachedItem.getBytes());
  }

  private static class CachedItem {
    private @Nullable byte[] bytes;
    private final @Nullable Callable<byte[]> dataFactory;

    public CachedItem(final @Nullable Callable<byte[]> dataFactory) {
      this.dataFactory = dataFactory;
    }

    public @NotNull byte[] getBytes() throws Exception {
      if (bytes == null && dataFactory != null) {
        bytes = dataFactory.call();
      }
      return orEmptyArray(bytes);
    }

    private static @NotNull byte[] orEmptyArray(final @Nullable byte[] bytes) {
      return bytes != null ? bytes : new byte[] {};
    }
  }

  @SuppressWarnings({"UnnecessaryParentheses"})
  private static byte[] serializeToMsgpack(final @NotNull Map<String, byte[]> map)
      throws IOException {
    try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

      // Write map header
      baos.write((byte) (0x80 | map.size()));

      // Iterate over the map and serialize each key-value pair
      for (final Map.Entry<String, byte[]> entry : map.entrySet()) {
        // Pack the key as a string
        final byte[] keyBytes = entry.getKey().getBytes(UTF_8);
        final int keyLength = keyBytes.length;
        // string up to 255 chars
        baos.write((byte) (0xd9));
        baos.write((byte) (keyLength));
        baos.write(keyBytes);

        // Pack the value as a binary string
        final byte[] valueBytes = entry.getValue();
        final int valueLength = valueBytes.length;
        // We will always use the 4 bytes data length for simplicity.
        baos.write((byte) (0xc6));
        baos.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(valueLength).array());
        baos.write(valueBytes);
      }

      return baos.toByteArray();
    }
  }
}
