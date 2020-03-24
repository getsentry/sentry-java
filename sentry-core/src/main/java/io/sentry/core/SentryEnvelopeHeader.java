package io.sentry.core;

import io.sentry.core.protocol.SentryId;
import io.sentry.core.util.Objects;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class SentryEnvelopeHeader {
  // Event Id must be set if the envelope holds an event, or an item that is related to the event
  // (e.g: attachments, user feedback)
  private final @NotNull SentryId eventId;

  public SentryEnvelopeHeader(final @NotNull SentryId eventId) {
    this.eventId = Objects.requireNonNull(eventId, "SentryId is required.");
  }

  public SentryEnvelopeHeader() {
    this(new SentryId());
  }

  public @NotNull SentryId getEventId() {
    return eventId;
  }
}
