package io.sentry;

import io.sentry.util.Objects;
import java.util.concurrent.Callable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SentryEnvelopeItemHeader {

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

  SentryEnvelopeItemHeader(
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
}
