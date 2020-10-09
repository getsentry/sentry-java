package io.sentry;

import java.util.UUID;

public final class SpanId {
  private final String value;

  public SpanId(String value) {
    this.value = value;
  }

  public SpanId() {
    this(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
  }

  @Override
  public String toString() {
    return this.value;
  }
}
