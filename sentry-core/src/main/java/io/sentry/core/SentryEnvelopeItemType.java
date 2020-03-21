package io.sentry.core;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public enum SentryEnvelopeItemType {
  Session("session"),
  Event("event");

  private final String type;

  SentryEnvelopeItemType(final String type) {
    this.type = type;
  }

  public String getType() {
    return type;
  }
}
