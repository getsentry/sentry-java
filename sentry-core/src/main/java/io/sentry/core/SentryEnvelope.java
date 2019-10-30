package io.sentry.core;

import io.sentry.core.protocol.SentryId;
import org.jetbrains.annotations.Nullable;

public final class SentryEnvelope {

  private final SentryEnvelopeHeader header;
  private final Iterable<SentryEnvelopeItem> items;

  public Iterable<SentryEnvelopeItem> getItems() {
    return items;
  }

  public SentryEnvelopeHeader getHeader() {
    return header;
  }

  public SentryEnvelope(SentryEnvelopeHeader header, Iterable<SentryEnvelopeItem> items) {
    this.header = header;
    this.items = items;
  }

  public SentryEnvelope(
      SentryId sentryId, @Nullable String auth, Iterable<SentryEnvelopeItem> items) {
    header = new SentryEnvelopeHeader(sentryId, auth);
    this.items = items;
  }

  public SentryEnvelope(SentryId sentryId, Iterable<SentryEnvelopeItem> items) {
    header = new SentryEnvelopeHeader(sentryId, null);
    this.items = items;
  }
}
