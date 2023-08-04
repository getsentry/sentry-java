package io.sentry.protocol;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonObjectReader;
import io.sentry.JsonSerializable;
import io.sentry.ObjectWriter;
import io.sentry.util.StringUtils;
import java.io.IOException;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SentryId implements JsonSerializable {
  private final @NotNull UUID uuid;

  public static final SentryId EMPTY_ID = new SentryId(new UUID(0, 0));

  public SentryId() {
    this((UUID) null);
  }

  public SentryId(@Nullable UUID uuid) {
    if (uuid == null) {
      uuid = UUID.randomUUID();
    }
    this.uuid = uuid;
  }

  public SentryId(final @NotNull String sentryIdString) {
    this.uuid = fromStringSentryId(StringUtils.normalizeUUID(sentryIdString));
  }

  @Override
  public String toString() {
    return StringUtils.normalizeUUID(uuid.toString()).replace("-", "");
  }

  @Override
  public boolean equals(final @Nullable Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SentryId sentryId = (SentryId) o;
    return uuid.compareTo(sentryId.uuid) == 0;
  }

  @Override
  public int hashCode() {
    return uuid.hashCode();
  }

  private @NotNull UUID fromStringSentryId(@NotNull String sentryIdString) {
    if (sentryIdString.length() == 32) {
      // expected format, SentryId is a UUID without dashes
      sentryIdString =
          new StringBuilder(sentryIdString)
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

  // JsonSerializable

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.value(toString());
  }

  // JsonElementDeserializer

  public static final class Deserializer implements JsonDeserializer<SentryId> {
    @Override
    public @NotNull SentryId deserialize(@NotNull JsonObjectReader reader, @NotNull ILogger logger)
        throws Exception {
      return new SentryId(reader.nextString());
    }
  }
}
