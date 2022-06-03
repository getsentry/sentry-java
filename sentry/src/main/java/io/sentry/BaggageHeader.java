package io.sentry;

import org.jetbrains.annotations.NotNull;

public final class BaggageHeader {
  public static final @NotNull String BAGGAGE_HEADER = "baggage";

  private final @NotNull String value;

  public BaggageHeader(final @NotNull String value) {
    this.value = value;
  }

  public BaggageHeader(final @NotNull Baggage baggage) {
    this.value = baggage.toHeaderString();
  }

  public @NotNull String getName() {
    return BAGGAGE_HEADER;
  }

  public @NotNull String getValue() {
    return value;
  }
}
