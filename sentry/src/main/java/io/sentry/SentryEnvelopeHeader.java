package io.sentry;

import io.sentry.protocol.SdkVersion;
import io.sentry.protocol.SentryId;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SentryEnvelopeHeader {
  // Event Id must be set if the envelope holds an event, or an item that is related to the event
  // (e.g: attachments, user feedback)
  private final @Nullable SentryId eventId;

  private final @Nullable SdkVersion sdkVersion;

  private final @Nullable TraceState trace;

  public SentryEnvelopeHeader(
      final @Nullable SentryId eventId, final @Nullable SdkVersion sdkVersion) {
    this(eventId, sdkVersion, null);
  }

  public SentryEnvelopeHeader(
      final @Nullable SentryId eventId,
      final @Nullable SdkVersion sdkVersion,
      final @Nullable TraceState trace) {
    this.eventId = eventId;
    this.sdkVersion = sdkVersion;
    this.trace = trace;
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

  public @Nullable SdkVersion getSdkVersion() {
    return sdkVersion;
  }

  public @Nullable TraceState getTrace() {
    return trace;
  }
}
