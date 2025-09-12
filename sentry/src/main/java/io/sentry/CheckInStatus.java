package io.sentry;

import java.util.Locale;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** Status of a CheckIn */
public enum CheckInStatus {
  IN_PROGRESS,
  OK,
  ERROR;

  public @NotNull String apiName() {
    return name().toLowerCase(Locale.ROOT);
  }
}
