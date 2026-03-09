package io.sentry;

import java.math.BigInteger;
import java.util.Collection;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum SentryAttributeType {
  STRING,
  BOOLEAN,
  INTEGER,
  DOUBLE,
  ARRAY;

  public @NotNull String apiName() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static @NotNull SentryAttributeType inferFrom(final @Nullable Object value) {
    if (value instanceof Boolean) {
      return BOOLEAN;
    }
    if (value instanceof Integer
        || value instanceof Long
        || value instanceof Short
        || value instanceof Byte
        || value instanceof BigInteger
        || value instanceof AtomicInteger
        || value instanceof AtomicLong) {
      return INTEGER;
    }
    if (value instanceof Number) {
      return DOUBLE;
    }
    if (value instanceof Collection || (value != null && value.getClass().isArray())) {
      return ARRAY;
    }
    return STRING;
  }
}
