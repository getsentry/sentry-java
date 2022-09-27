package io.sentry;

import java.util.Locale;
import org.jetbrains.annotations.NotNull;

public enum SentryMeasurementUnit {
  /** Nanosecond (`"nanosecond"`), 10^-9 seconds. */
  NANOSECOND,

  /** Microsecond (`"microsecond"`), 10^-6 seconds. */
  MICROSECOND,

  /** Millisecond (`"millisecond"`), 10^-3 seconds. */
  MILLISECOND,

  /** Full second (`"second"`). */
  SECOND,

  /** Minute (`"minute"`), 60 seconds. */
  MINUTE,

  /** Hour (`"hour"`), 3600 seconds. */
  HOUR,

  /** Day (`"day"`), 86,400 seconds. */
  DAY,

  /** Week (`"week"`), 604,800 seconds. */
  WEEK,

  /** Untyped value without a unit. */
  NONE;

  public @NotNull String apiName() {
    return name().toLowerCase(Locale.ROOT);
  }
}
