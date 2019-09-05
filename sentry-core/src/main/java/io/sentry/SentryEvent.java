package io.sentry;

import java.time.Instant;
import java.util.UUID;

public class SentryEvent {
  private UUID eventUuid;
  private Instant eventInstant;

  SentryEvent(UUID eventUuid, Instant instant) {
    this.eventUuid = eventUuid;
    this.eventInstant = instant;
  }

  public SentryEvent() {
    this(UUID.randomUUID(), Instant.now());
  }

  public UUID getEventId() {
    return eventUuid;
  }

  public Instant getTimestamp() {
    return eventInstant;
  }
}
