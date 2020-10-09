package io.sentry;

import io.sentry.protocol.SdkVersion;
import io.sentry.protocol.SentryId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;

/**
 * An item sent to Sentry in the envelope. Can be either {@link SentryEvent} or the Performance
 * transaction.
 */
public abstract class SentryBaseEvent<T extends ConcurrentHashMap<String, Object>> implements ConvertibleToEnvelopeItem {
  private @Nullable SentryId eventId;
  private T contexts;
  private @Nullable SdkVersion sdk;

  protected SentryBaseEvent(final @NotNull SentryId eventId) {
    this.eventId = eventId;
  }

  protected SentryBaseEvent() {
    this(new SentryId());
  }

  public @Nullable SentryId getEventId() {
    return eventId;
  }

  public void setEventId(@Nullable SentryId eventId) {
    this.eventId = eventId;
  }

  public T getContexts() {
    return contexts;
  }

  public void setContexts(T contexts) {
    this.contexts = contexts;
  }

  // todo: add @nullable
  public SdkVersion getSdk() {
    return sdk;
  }

  public void setSdk(@Nullable SdkVersion sdk) {
    this.sdk = sdk;
  }
}
