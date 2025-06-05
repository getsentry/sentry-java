package io.sentry;

import java.util.Locale;
import org.jetbrains.annotations.NotNull;

public enum SentryAttributeType {
  STRING,
  BOOLEAN,
  INTEGER,
  DOUBLE;

  public @NotNull String apiName() {
    return name().toLowerCase(Locale.ROOT);
  }
}
