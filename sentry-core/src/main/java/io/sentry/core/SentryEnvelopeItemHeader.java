package io.sentry.core;

public final class SentryEnvelopeItemHeader {
  private final String contentType;
  private final String fileName;
  private final String type;
  private final int length;

  public String getType() {
    return type;
  }

  public int getLength() {
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
  }
}
