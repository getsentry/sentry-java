package io.sentry.cache;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonSerializable;
import io.sentry.ObjectReader;
import io.sentry.ObjectWriter;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Lightweight metadata for a scope attachment that can be persisted to disk. Only pathname-based
 * attachments are supported — byte-array, callable, and serializable attachments are not
 * persistable.
 */
@ApiStatus.Internal
public final class PersistedAttachment implements JsonSerializable {

  private final @NotNull String pathname;
  private final @NotNull String filename;
  private final @Nullable String contentType;
  private final @Nullable String attachmentType;

  public PersistedAttachment(
      final @NotNull String pathname,
      final @NotNull String filename,
      final @Nullable String contentType,
      final @Nullable String attachmentType) {
    this.pathname = pathname;
    this.filename = filename;
    this.contentType = contentType;
    this.attachmentType = attachmentType;
  }

  public @NotNull String getPathname() {
    return pathname;
  }

  public @NotNull String getFilename() {
    return filename;
  }

  public @Nullable String getContentType() {
    return contentType;
  }

  public @Nullable String getAttachmentType() {
    return attachmentType;
  }

  @Override
  public boolean equals(final @Nullable Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final PersistedAttachment that = (PersistedAttachment) o;
    return pathname.equals(that.pathname)
        && filename.equals(that.filename)
        && java.util.Objects.equals(contentType, that.contentType)
        && java.util.Objects.equals(attachmentType, that.attachmentType);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(pathname, filename, contentType, attachmentType);
  }

  // region JsonSerializable

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    writer.name("pathname").value(pathname);
    writer.name("filename").value(filename);
    if (contentType != null) {
      writer.name("content_type").value(contentType);
    }
    if (attachmentType != null) {
      writer.name("attachment_type").value(attachmentType);
    }
    writer.endObject();
  }

  // endregion

  // region JsonDeserializer

  public static final class Deserializer implements JsonDeserializer<PersistedAttachment> {
    @Override
    public @NotNull PersistedAttachment deserialize(
        final @NotNull ObjectReader reader, final @NotNull ILogger logger) throws Exception {
      String pathname = null;
      String filename = null;
      String contentType = null;
      String attachmentType = null;

      reader.beginObject();
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case "pathname":
            pathname = reader.nextStringOrNull();
            break;
          case "filename":
            filename = reader.nextStringOrNull();
            break;
          case "content_type":
            contentType = reader.nextStringOrNull();
            break;
          case "attachment_type":
            attachmentType = reader.nextStringOrNull();
            break;
          default:
            reader.skipValue();
            break;
        }
      }
      reader.endObject();

      if (pathname == null || filename == null) {
        throw new IllegalStateException(
            "Missing required fields for PersistedAttachment: pathname and filename");
      }

      return new PersistedAttachment(pathname, filename, contentType, attachmentType);
    }
  }

  // endregion
}
