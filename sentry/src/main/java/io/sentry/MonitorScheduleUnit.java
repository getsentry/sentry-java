package io.sentry;

import java.util.Locale;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** Time unit of a monitor schedule. */
public enum MonitorScheduleUnit {
  MINUTE,
  HOUR,
  DAY,
  WEEK,
  MONTH,
  YEAR;

  public @NotNull String apiName() {
    return name().toLowerCase(Locale.ROOT);
  }
}
