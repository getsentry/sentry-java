package io.sentry.protocol;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonSerializable;
import io.sentry.ObjectReader;
import io.sentry.ObjectWriter;
import io.sentry.util.LazyEvaluator;
import io.sentry.util.StringUtils;
import java.io.IOException;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SentryId implements JsonSerializable {

  public static final SentryId EMPTY_ID = new SentryId(new UUID(0, 0));

  private final @NotNull LazyEvaluator<UUID> lazyValue;

  public SentryId() {
    this((UUID) null);
  }

  public SentryId(@Nullable UUID uuid) {
    if (uuid != null) {
      this.lazyValue = new LazyEvaluator<>(() -> uuid);
    } else {
      this.lazyValue = new LazyEvaluator<>(UUID::randomUUID);
    }
  }

  public SentryId(final @NotNull String sentryIdString) {
    if (sentryIdString.length() != 32 && sentryIdString.length() != 36) {
      throw new IllegalArgumentException(
        "String representation of SentryId has either 32 (UUID no dashes) "
          + "or 36 characters long (completed UUID). Received: "
          + sentryIdString);
    }
    this.lazyValue =
        new LazyEvaluator<>(() -> fromStringSentryId(StringUtils.normalizeUUID(sentryIdString)));
  }

  @Override
  public String toString() {
    return StringUtils.normalizeUUID(lazyValue.getValue().toString()).replace("-", "");
  }

  @Override
  public boolean equals(final @Nullable Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SentryId sentryId = (SentryId) o;
    return lazyValue.getValue().compareTo(sentryId.lazyValue.getValue()) == 0;
  }

  @Override
  public int hashCode() {
    return lazyValue.getValue().hashCode();
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
    public @NotNull SentryId deserialize(@NotNull ObjectReader reader, @NotNull ILogger logger)
        throws Exception {
      return new SentryId(reader.nextString());
    }
  }
}
