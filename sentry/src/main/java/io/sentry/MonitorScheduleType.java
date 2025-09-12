package io.sentry;

import java.util.Locale;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** Type of a monitor schedule */
public enum MonitorScheduleType {
  CRONTAB,
  INTERVAL;

  public @NotNull String apiName() {
    return name().toLowerCase(Locale.ROOT);
  }
}
