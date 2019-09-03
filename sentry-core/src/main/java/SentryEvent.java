import java.util.UUID;

public class SentryEvent {
  private UUID eventId;

  SentryEvent(UUID eventId) {
    this.eventId = eventId;
  }

  public SentryEvent() {
    this(UUID.randomUUID());
  }

  public UUID getEventId() {
    return eventId;
  }
}
