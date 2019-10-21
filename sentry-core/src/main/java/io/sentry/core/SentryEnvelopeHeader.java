package io.sentry.core;

import io.sentry.core.protocol.SentryId;
import io.sentry.core.util.Nullable;

public final class SentryEnvelopeHeader {
  private final SentryId eventId;
  private final String auth;

  public SentryEnvelopeHeader(SentryId sentryId, @Nullable String auth) {
    this.eventId = sentryId;
    this.auth = auth;
  }

  public SentryEnvelopeHeader(SentryId sentryId) {
    this(sentryId, null);
  }

  public SentryId getEventId() {
    return eventId;
  }

  public String getAuth() {
    return auth;
  }
}
