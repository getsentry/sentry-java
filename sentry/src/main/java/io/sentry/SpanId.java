package io.sentry;

import static io.sentry.util.StringUtils.PROPER_NIL_UUID;

import io.sentry.util.Objects;
import io.sentry.util.StringUtils;
import java.io.IOException;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

public final class SpanId implements JsonSerializable {
  public static final SpanId EMPTY_ID = new SpanId(PROPER_NIL_UUID.replace("-", ""));

  private final @NotNull String value;

  public SpanId(final @NotNull String value) {
    this.value = Objects.requireNonNull(value, "value is required");
  }

  public SpanId() {
    this(SentryUUID.generateSpanId());
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

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.value(value);
  }

  // JsonElementDeserializer

  public static final class Deserializer implements JsonDeserializer<SpanId> {
    @Override
    public @NotNull SpanId deserialize(@NotNull ObjectReader reader, @NotNull ILogger logger)
        throws Exception {
      return new SpanId(reader.nextString());
    }
  }
}
