package io.sentry;

import io.sentry.protocol.SentryId;
import org.jetbrains.annotations.NotNull;

/**
 * An item sent to Sentry in the envelope. Can be either {@link SentryEvent} or the Performance
 * transaction.
 */
public abstract class SentryItem implements ConvertibleToEnvelopeItem {
  private @NotNull SentryId eventId;

  protected SentryItem(final @NotNull SentryId eventId) {
    this.eventId = eventId;
  }

  protected SentryItem() {
    this(new SentryId());
  }

  public SentryId getEventId() {
    return eventId;
  }

  public void setEventId(SentryId eventId) {
    this.eventId = eventId;
  }
}
