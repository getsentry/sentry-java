package io.sentry.core;

import io.sentry.core.util.Objects;
import java.util.concurrent.Callable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SentryEnvelopeItemHeader {

  private final @Nullable String contentType;
  private final @Nullable String fileName;
  private final @NotNull String type;
  private final int length;
  @Nullable private final Callable<Integer> getLength;

  // TODO: Looks like a type here that defaults to String for unknown values would be ideal
  public @NotNull String getType() {
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

  SentryEnvelopeItemHeader(
      final @NotNull String type,
      int length,
      final @NotNull String contentType,
      final @Nullable String fileName) {
    this.type = Objects.requireNonNull(type, "type is required");
    this.contentType = contentType;
    this.length = length;
    this.fileName = fileName;
    this.getLength = null;
  }

  SentryEnvelopeItemHeader(
      final @NotNull String type,
      final @Nullable Callable<Integer> getLength,
      final @Nullable String contentType,
      final @Nullable String fileName) {
    this.type = Objects.requireNonNull(type, "type is required");
    this.contentType = contentType;
    this.length = -1;
    this.fileName = fileName;
    this.getLength = getLength;
  }
}
