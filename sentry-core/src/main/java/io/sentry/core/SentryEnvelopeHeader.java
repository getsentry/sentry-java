package io.sentry.core;

import io.sentry.core.protocol.SentryId;
import io.sentry.core.util.Objects;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SentryEnvelopeHeader {
  // Event Id must be set if the envelope holds an event, or an item that is related to the event
  // (e.g: attachments, user feedback)
  private final @NotNull SentryId eventId;
  // TODO: I noticed this dropped from the spec
  // Should be safe to delete since this was an optional field which was never used
  // Nothing serialized anywhere should have this value, if it's there we can just drop it.
  private final @Nullable String auth;

  SentryEnvelopeHeader(final @NotNull SentryId eventId, final @Nullable String auth) {
    this.eventId = Objects.requireNonNull(eventId, "SentryId is required.");
    this.auth = auth;
  }

  public SentryEnvelopeHeader(final @NotNull SentryId sentryId) {
    this(sentryId, null);
  }

  public SentryEnvelopeHeader() {
    this(new SentryId(), null);
  }

  // TODO Should be renamed to EnvelopeId
  public @NotNull SentryId getEventId() {
    return eventId;
  }

  public @Nullable String getAuth() {
    return auth;
  }
}
