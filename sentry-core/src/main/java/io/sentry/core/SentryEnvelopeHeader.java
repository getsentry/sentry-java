package io.sentry.core;

import io.sentry.core.protocol.SdkInfo;
import io.sentry.core.protocol.SentryId;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SentryEnvelopeHeader {
  // Event Id must be set if the envelope holds an event, or an item that is related to the event
  // (e.g: attachments, user feedback)
  private final @Nullable SentryId eventId;

  private final @Nullable SdkInfo sdkInfo;

  public SentryEnvelopeHeader(final @Nullable SentryId eventId, final @Nullable SdkInfo sdkInfo) {
    this.eventId = eventId;
    this.sdkInfo = sdkInfo;
  }

  public SentryEnvelopeHeader(final @Nullable SentryId eventId) {
    this(eventId, null);
  }

  public SentryEnvelopeHeader() {
    this(new SentryId());
  }

  public @Nullable SentryId getEventId() {
    return eventId;
  }

  public @Nullable SdkInfo getSdkInfo() {
    return sdkInfo;
  }
}
