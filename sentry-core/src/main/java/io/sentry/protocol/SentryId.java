package io.sentry.protocol;

import java.util.UUID;

public final class SentryId {
  private final UUID uuid;

  public static final SentryId EMPTY_ID = new SentryId(new UUID(0, 0));

  public SentryId() {
    this(null);
  }

  public SentryId(UUID uuid) {
    if (uuid == null) {
      uuid = UUID.randomUUID();
    }
    this.uuid = uuid;
  }

  @Override
  public String toString() {
    return uuid.toString().replace("-", "");
  }
}
