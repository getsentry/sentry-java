package io.sentry;

import io.sentry.util.Objects;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SentryEnvelopeItemHeader implements JsonSerializable, JsonUnknown {

  private final @Nullable String contentType;
  private final @Nullable Integer itemCount;
  private final @Nullable String fileName;
  private final @Nullable String platform;
  private final @NotNull SentryItemType type;
  private final int length;
  @Nullable private final Callable<Integer> getLength;
  private final @Nullable String attachmentType;

  private @Nullable Map<String, Object> unknown;

  public @NotNull SentryItemType getType() {
    return type;
  }

  public int getLength() {
    if (getLength != null) {
      try {
        return getLength.call();
      } catch (Throwable ignored) {
        // TODO: Take ILogger via ctor or hook into internal static logger
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

  public @Nullable String getPlatform() {
    return platform;
  }

  @ApiStatus.Internal
  public SentryEnvelopeItemHeader(
      final @NotNull SentryItemType type,
      int length,
      final @Nullable String contentType,
      final @Nullable String fileName,
      final @Nullable String attachmentType,
      final @Nullable String platform,
      final @Nullable Integer itemCount) {
    this.type = Objects.requireNonNull(type, "type is required");
    this.contentType = contentType;
    this.length = length;
    this.fileName = fileName;
    this.getLength = null;
    this.attachmentType = attachmentType;
    this.platform = platform;
    this.itemCount = itemCount;
  }

  SentryEnvelopeItemHeader(
      final @NotNull SentryItemType type,
      final @Nullable Callable<Integer> getLength,
      final @Nullable String contentType,
      final @Nullable String fileName,
      final @Nullable String attachmentType) {
    this(type, getLength, contentType, fileName, attachmentType, null, null);
  }

  SentryEnvelopeItemHeader(
      final @NotNull SentryItemType type,
      final @Nullable Callable<Integer> getLength,
      final @Nullable String contentType,
      final @Nullable String fileName,
      final @Nullable String attachmentType,
      final @Nullable String platform,
      final @Nullable Integer itemCount) {
    this.type = Objects.requireNonNull(type, "type is required");
    this.contentType = contentType;
    this.length = -1;
    this.fileName = fileName;
    this.getLength = getLength;
    this.attachmentType = attachmentType;
    this.platform = platform;
    this.itemCount = itemCount;
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
    public static final String PLATFORM = "platform";
    public static final String ITEM_COUNT = "item_count";
  }

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
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
    if (platform != null) {
      writer.name(JsonKeys.PLATFORM).value(platform);
    }
    if (itemCount != null) {
      writer.name(JsonKeys.ITEM_COUNT).value(itemCount);
    }
    writer.name(JsonKeys.LENGTH).value(getLength());
    if (unknown != null) {
      for (String key : unknown.keySet()) {
        Object value = unknown.get(key);
        writer.name(key);
        writer.value(logger, value);
      }
    }
    writer.endObject();
  }

  public static final class Deserializer implements JsonDeserializer<SentryEnvelopeItemHeader> {
    @Override
    public @NotNull SentryEnvelopeItemHeader deserialize(
        @NotNull ObjectReader reader, @NotNull ILogger logger) throws Exception {
      reader.beginObject();

      String contentType = null;
      String fileName = null;
      SentryItemType type = null;
      int length = 0;
      String attachmentType = null;
      String platform = null;
      Integer itemCount = null;
      Map<String, Object> unknown = null;

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
            type = reader.nextOrNull(logger, new SentryItemType.Deserializer());
            break;
          case JsonKeys.LENGTH:
            length = reader.nextInt();
            break;
          case JsonKeys.ATTACHMENT_TYPE:
            attachmentType = reader.nextStringOrNull();
            break;
          case JsonKeys.PLATFORM:
            platform = reader.nextStringOrNull();
            break;
          case JsonKeys.ITEM_COUNT:
            itemCount = reader.nextIntegerOrNull();
            break;
          default:
            if (unknown == null) {
              unknown = new HashMap<>();
            }
            reader.nextUnknown(logger, unknown, nextName);
            break;
        }
      }
      if (type == null) {
        throw missingRequiredFieldException(JsonKeys.TYPE, logger);
      }
      SentryEnvelopeItemHeader sentryEnvelopeItemHeader =
          new SentryEnvelopeItemHeader(
              type, length, contentType, fileName, attachmentType, platform, itemCount);
      sentryEnvelopeItemHeader.setUnknown(unknown);
      reader.endObject();
      return sentryEnvelopeItemHeader;
    }

    @SuppressWarnings("SameParameterValue")
    private Exception missingRequiredFieldException(String field, ILogger logger) {
      String message = "Missing required field \"" + field + "\"";
      Exception exception = new IllegalStateException(message);
      logger.log(SentryLevel.ERROR, message, exception);
      return exception;
    }
  }

  // JsonUnknown

  @Nullable
  @Override
  public Map<String, Object> getUnknown() {
    return unknown;
  }

  @Override
  public void setUnknown(@Nullable Map<String, Object> unknown) {
    this.unknown = unknown;
  }
}
