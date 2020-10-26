package io.sentry;

import java.util.UUID;
import org.jetbrains.annotations.NotNull;

public final class SpanId {
  private final @NotNull String value;

  public SpanId(final @NotNull String value) {
    this.value = value;
  }

  public SpanId() {
    this(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
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
}
