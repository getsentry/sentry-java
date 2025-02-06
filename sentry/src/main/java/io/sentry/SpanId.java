package io.sentry;

import static io.sentry.util.StringUtils.PROPER_NIL_UUID;

import io.sentry.util.LazyEvaluator;
import java.io.IOException;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public final class SpanId implements JsonSerializable {
  public static final SpanId EMPTY_ID =
      new SpanId(PROPER_NIL_UUID.replace("-", "").substring(0, 16));

  private final @NotNull LazyEvaluator<String> lazyValue;

  public SpanId(final @NotNull String value) {
    Objects.requireNonNull(value, "value is required");
    this.lazyValue = new LazyEvaluator<>(() -> value);
  }

  public SpanId() {
    this.lazyValue = new LazyEvaluator<>(SentryUUID::generateSpanId);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SpanId spanId = (SpanId) o;
    return lazyValue.getValue().equals(spanId.lazyValue.getValue());
  }

  @Override
  public int hashCode() {
    return lazyValue.getValue().hashCode();
  }

  @Override
  public String toString() {
    return lazyValue.getValue();
  }

  // JsonElementSerializer

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.value(lazyValue.getValue());
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
