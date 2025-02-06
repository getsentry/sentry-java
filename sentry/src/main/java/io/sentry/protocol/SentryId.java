package io.sentry.protocol;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonSerializable;
import io.sentry.ObjectReader;
import io.sentry.ObjectWriter;
import io.sentry.SentryUUID;
import io.sentry.util.LazyEvaluator;
import io.sentry.util.StringUtils;
import io.sentry.util.UUIDStringUtils;
import java.io.IOException;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SentryId implements JsonSerializable {

  public static final SentryId EMPTY_ID =
      new SentryId(StringUtils.PROPER_NIL_UUID.replace("-", ""));

  private final @NotNull LazyEvaluator<String> lazyStringValue;

  public SentryId() {
    this((UUID) null);
  }

  public SentryId(@Nullable UUID uuid) {
    if (uuid != null) {
      this.lazyStringValue =
          new LazyEvaluator<>(() -> normalize(UUIDStringUtils.toSentryIdString(uuid)));
    } else {
      this.lazyStringValue = new LazyEvaluator<>(SentryUUID::generateSentryId);
    }
  }

  public SentryId(final @NotNull String sentryIdString) {
    final @NotNull String normalized = StringUtils.normalizeUUID(sentryIdString);
    if (normalized.length() != 32 && normalized.length() != 36) {
      throw new IllegalArgumentException(
          "String representation of SentryId has either 32 (UUID no dashes) "
              + "or 36 characters long (completed UUID). Received: "
              + sentryIdString);
    }
    if (normalized.length() == 36) {
      this.lazyStringValue = new LazyEvaluator<>(() -> normalize(normalized));
    } else {
      this.lazyStringValue = new LazyEvaluator<>(() -> normalized);
    }
  }

  @Override
  public String toString() {
    return lazyStringValue.getValue();
  }

  @Override
  public boolean equals(final @Nullable Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SentryId sentryId = (SentryId) o;
    return lazyStringValue.getValue().equals(sentryId.lazyStringValue.getValue());
  }

  @Override
  public int hashCode() {
    return lazyStringValue.getValue().hashCode();
  }

  private @NotNull String normalize(@NotNull String uuidString) {
    return StringUtils.normalizeUUID(uuidString).replace("-", "");
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
