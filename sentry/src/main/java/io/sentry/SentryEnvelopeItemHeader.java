package io.sentry;

import io.sentry.util.Objects;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.concurrent.Callable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SentryEnvelopeItemHeader implements JsonSerializable {

  private final @Nullable String contentType;
  private final @Nullable String fileName;
  private final @NotNull SentryItemType type;
  private final int length;
  @Nullable private final Callable<Integer> getLength;
  private final @Nullable String attachmentType;

  public @NotNull SentryItemType getType() {
    return type;
  }

  public int getLength() {
    if (getLength != null) {
      try {
        return getLength.call();
      } catch (Exception ignored) {
        return -1;
      }
    }
    return length;
  }

  public @Nullable String getContentType() {
    return contentType;
  }

  public @Nullable String getFileName() {
    return fileName;
  }

  @ApiStatus.Internal
  public SentryEnvelopeItemHeader(
      final @NotNull SentryItemType type,
      int length,
      final @Nullable String contentType,
      final @Nullable String fileName,
      final @Nullable String attachmentType) {
    this.type = Objects.requireNonNull(type, "type is required");
    this.contentType = contentType;
    this.length = length;
    this.fileName = fileName;
    this.getLength = null;
    this.attachmentType = attachmentType;
  }

  SentryEnvelopeItemHeader(
      final @NotNull SentryItemType type,
      final @Nullable Callable<Integer> getLength,
      final @Nullable String contentType,
      final @Nullable String fileName,
      final @Nullable String attachmentType) {
    this.type = Objects.requireNonNull(type, "type is required");
    this.contentType = contentType;
    this.length = -1;
    this.fileName = fileName;
    this.getLength = getLength;
    this.attachmentType = attachmentType;
  }

  SentryEnvelopeItemHeader(
      final @NotNull SentryItemType type,
      final @Nullable Callable<Integer> getLength,
      final @Nullable String contentType,
      final @Nullable String fileName) {
    this(type, getLength, contentType, fileName, null);
  }

  /**
   * Returns the attachmentType type
   *
   * @return the attachmentType
   */
  public @Nullable String getAttachmentType() {
    return attachmentType;
  }

  // JsonSerializable

  public static final class JsonKeys {
    public static final String CONTENT_TYPE = "content_type";
    public static final String FILENAME = "filename";
    public static final String TYPE = "type";
    public static final String ATTACHMENT_TYPE = "attachment_type";
    public static final String LENGTH = "length";
  }

  @Override
  public void serialize(@NotNull JsonObjectWriter writer, @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    if (contentType != null) {
      writer.name(JsonKeys.CONTENT_TYPE).value(contentType);
    }
    if (fileName != null) {
      writer.name(JsonKeys.FILENAME).value(fileName);
    }
    writer.name(JsonKeys.TYPE).value(logger, type);
    if (attachmentType != null) {
      writer.name(JsonKeys.ATTACHMENT_TYPE).value(attachmentType);
    }
    writer.name(JsonKeys.LENGTH).value(length);
    writer.endObject();
  }

  public static final class Deserializer implements JsonDeserializer<SentryEnvelopeItemHeader> {
    @Override
    public @NotNull SentryEnvelopeItemHeader deserialize(
        @NotNull JsonObjectReader reader, @NotNull ILogger logger) throws Exception {
      reader.beginObject();

      String contentType = null;
      String fileName = null;
      SentryItemType type = null;
      int length = 0;
      String attachmentType = null;

      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.CONTENT_TYPE:
            contentType = reader.nextStringOrNull();
            break;
          case JsonKeys.FILENAME:
            fileName = reader.nextStringOrNull();
            break;
          case JsonKeys.TYPE:
            type = new SentryItemType.Deserializer().deserialize(reader, logger);
            break;
          case JsonKeys.LENGTH:
            length = reader.nextInt();
            break;
          case JsonKeys.ATTACHMENT_TYPE:
            attachmentType = reader.nextStringOrNull();
            break;
          default:
            break;
        }
      }
      if (type == null) {
        throw missingRequiredFieldException(JsonKeys.TYPE, logger);
      }
      SentryEnvelopeItemHeader sentryEnvelopeHeader =
          new SentryEnvelopeItemHeader(type, length, contentType, fileName, attachmentType);
      reader.endObject();
      return sentryEnvelopeHeader;
    }

    @SuppressWarnings("SameParameterValue")
    private Exception missingRequiredFieldException(String field, ILogger logger) {
      String message = "Missing required field \"" + field + "\"";
      Exception exception = new IllegalStateException(message);
      logger.log(SentryLevel.ERROR, message, exception);
      return exception;
    }
  }
}
