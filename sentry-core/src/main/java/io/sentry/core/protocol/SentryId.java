package io.sentry.core.protocol;

import java.util.UUID;

public final class SentryId {
  private final UUID uuid;

  public static final SentryId EMPTY_ID = new SentryId(new UUID(0, 0));

  public SentryId() {
    this((UUID) null);
  }

  public SentryId(UUID uuid) {
    if (uuid == null) {
      uuid = UUID.randomUUID();
    }
    this.uuid = uuid;
  }

  public SentryId(String sentryIdString) {
    this.uuid = fromStringSentryId(sentryIdString);
  }

  @Override
  public String toString() {
    return uuid.toString().replace("-", "");
  }

  private UUID fromStringSentryId(String sentryIdString) {
    if (sentryIdString == null) {
      return null;
    }

    if (sentryIdString.length() == 32) {
      // expected format, SentryId is a UUID without dashes
      sentryIdString =
          new StringBuffer(sentryIdString)
              .insert(8, "-")
              .insert(13, "-")
              .insert(18, "-")
              .insert(23, "-")
              .toString();
    }
    if (sentryIdString.length() != 36) {
      throw new IllegalArgumentException(
          "String representation of SentryId has either 32 (UUID no dashes) "
              + "or 36 characters long (completed UUID). Received: "
              + sentryIdString);
    }

    return UUID.fromString(sentryIdString);
  }
}
