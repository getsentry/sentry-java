package io.sentry.core;

import io.sentry.core.protocol.SentryId;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SentryEnvelope {

  // types: session_batch, session, event, attachment
  // an envelope cannot have 2 events, but eg multiple sessions
  private final @NotNull SentryEnvelopeHeader header;
  private final @NotNull Iterable<SentryEnvelopeItem> items;

  public Iterable<SentryEnvelopeItem> getItems() {
    return items;
  }

  public @NotNull SentryEnvelopeHeader getHeader() {
    return header;
  }

  public SentryEnvelope(
      @NotNull SentryEnvelopeHeader header, @NotNull Iterable<SentryEnvelopeItem> items) {
    this.header = header;
    this.items = items;
  }

  public SentryEnvelope(
      SentryId sentryId, @Nullable String auth, Iterable<SentryEnvelopeItem> items) {
    header = new SentryEnvelopeHeader(sentryId, auth);
    this.items = items;
  }

  public SentryEnvelope(SentryId sentryId, Iterable<SentryEnvelopeItem> items) {
    header = new SentryEnvelopeHeader(sentryId);
    this.items = items;
  }

  // Single item envelope with an envelope-allocated id
  public SentryEnvelope(SentryEnvelopeItem item) {
    header = new SentryEnvelopeHeader();
    List<SentryEnvelopeItem> items = new ArrayList<>(1);
    items.add(item);
    this.items = items;
  }

  public static SentryEnvelope fromSession(ISerializer serializer, Session session)
      throws IOException {
    return new SentryEnvelope(SentryEnvelopeItem.fromSession(serializer, session));
  }
}
