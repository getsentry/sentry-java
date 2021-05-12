package io.sentry.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class Objects {
  private Objects() {}

  public static <T> T requireNonNull(final @Nullable T obj, final @NotNull String message) {
    if (obj == null) throw new IllegalArgumentException(message);
    return obj;
  }
}
