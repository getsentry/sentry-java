package io.sentry.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class Pair<A, B> {
  private final @Nullable A first;
  private final @Nullable B second;

  public Pair(final @Nullable A first, final @Nullable B second) {
    this.first = first;
    this.second = second;
  }

  public @Nullable A getFirst() {
    return first;
  }

  public @Nullable B getSecond() {
    return second;
  }
}
