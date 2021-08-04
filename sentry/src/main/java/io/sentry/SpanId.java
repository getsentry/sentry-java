package io.sentry;

import io.sentry.util.Objects;
import java.io.IOException;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

public final class SpanId {
  public static final SpanId EMPTY_ID = new SpanId(new UUID(0, 0).toString());

  private final @NotNull String value;

  public SpanId(final @NotNull String value) {
    this.value = Objects.requireNonNull(value, "value is required");
  }

  public SpanId() {
    this(UUID.randomUUID());
  }

  private SpanId(final @NotNull UUID uuid) {
    this(uuid.toString().replace("-", "").substring(0, 16));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SpanId spanId = (SpanId) o;
    return value.equals(spanId.value);
  }

  @Override
  public int hashCode() {
    return value.hashCode();
  }

  @Override
  public String toString() {
    return this.value;
  }

  // JsonElementSerializer

  public static final class Serializer implements JsonElementSerializer<SpanId> {
    @Override
    public void serialize(SpanId src, @NotNull JsonObjectWriter writer) throws IOException {
      writer.value(src.value);
    }
  }

  // JsonElementDeserializer

  public static final class Deserializer implements JsonElementDeserializer<SpanId> {
    @Override
    public @NotNull SpanId deserialize(@NotNull JsonObjectReader reader) throws IOException {
      return new SpanId(reader.nextString());
    }
  }
}
