package io.sentry.core;

import java.util.concurrent.Callable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SentryEnvelopeItemHeader {
  private final String contentType;
  private final String fileName;
  private final String type;
  private final int length;
  @Nullable private final Callable<Integer> getLength;

  // TODO: Looks like a type here that defaults to String for unknown values would be ideal
  public String getType() {
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

  public String getContentType() {
    return contentType;
  }

  public String getFileName() {
    return fileName;
  }

  SentryEnvelopeItemHeader(String type, int length, String contentType, String fileName) {
    this.type = type;
    this.length = length;
    this.contentType = contentType;
    this.fileName = fileName;
    this.getLength = null;
  }

  SentryEnvelopeItemHeader(
      String type, @Nullable Callable<Integer> getLength, String contentType, String fileName) {
    this.type = type;
    this.length = -1;
    this.contentType = contentType;
    this.fileName = fileName;
    this.getLength = getLength;
  }
}
