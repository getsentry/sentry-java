package io.sentry;

import static io.sentry.util.StringUtils.PROPER_NIL_UUID;

import java.io.IOException;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SpanId implements JsonSerializable {
  public static final SpanId EMPTY_ID =
      new SpanId(PROPER_NIL_UUID.replace("-", "").substring(0, 16));

  private volatile @Nullable String value;

  public SpanId(final @NotNull String value) {
    this.value = Objects.requireNonNull(value, "value is required");
  }

  public SpanId() {}

  private @NotNull String getValue() {
    String result = value;
    if (result == null) {
      synchronized (this) {
        result = value;
        if (result == null) {
          result = SentryUUID.generateSpanId();
          value = result;
        }
      }
    }
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SpanId spanId = (SpanId) o;
    return getValue().equals(spanId.getValue());
  }

  @Override
  public int hashCode() {
    return getValue().hashCode();
  }

  @Override
  public String toString() {
    return getValue();
  }

  // JsonElementSerializer

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.value(getValue());
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
