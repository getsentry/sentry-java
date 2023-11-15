package io.sentry;

import static io.sentry.util.FileUtils.readBytesFromFile;
import static io.sentry.vendor.Base64.NO_PADDING;
import static io.sentry.vendor.Base64.NO_WRAP;

import io.sentry.clientreport.ClientReport;
import io.sentry.exception.SentryEnvelopeException;
import io.sentry.protocol.SentryTransaction;
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
                final @Nullable byte[] data =
                    JsonSerializationUtils.bytesFrom(serializer, logger, serializable);

                if (data != null) {
                  ensureAttachmentSizeLimit(
                      data.length, maxAttachmentSize, attachment.getFilename());
                  return data;
                }
              } else if (attachment.getPathname() != null) {
                return readBytesFromFile(attachment.getPathname(), maxAttachmentSize);
              }
              throw new SentryEnvelopeException(
                  String.format(
                      "Couldn't attach the attachment %s.\n"
                          + "Please check that either bytes, serializable or a path is set.",
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

  public static @NotNull SentryEnvelopeItem fromProfilingTrace(
      final @NotNull ProfilingTraceData profilingTraceData,
      final long maxTraceFileSize,
      final @NotNull ISerializer serializer)
      throws SentryEnvelopeException {

    File traceFile = profilingTraceData.getTraceFile();
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
              byte[] traceFileBytes = readBytesFromFile(traceFile.getPath(), maxTraceFileSize);
              String base64Trace = Base64.encodeToString(traceFileBytes, NO_WRAP | NO_PADDING);
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
}
