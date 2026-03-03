package io.sentry;

import java.util.Locale;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum SentryAttributeType {
  STRING,
  BOOLEAN,
  INTEGER,
  DOUBLE;

  public @NotNull String apiName() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static @NotNull SentryAttributeType inferFrom(final @Nullable Object value) {
    if (value instanceof Boolean) {
      return BOOLEAN;
    }
    if (value instanceof Integer) {
      return INTEGER;
    }
    if (value instanceof Number) {
      return DOUBLE;
    }
    return STRING;
  }
}
