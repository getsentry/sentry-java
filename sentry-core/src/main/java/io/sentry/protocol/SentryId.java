package io.sentry.protocol;

import java.util.UUID;

public class SentryId {
  private final UUID uuid;

  public static final SentryId EMPTY_ID =
      new SentryId(UUID.fromString("00000000-0000-0000-0000-000000000000"));

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
