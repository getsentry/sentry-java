package io.sentry;

import java.util.Locale;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * <a href="https://getsentry.github.io/relay/relay_metrics/enum.MetricUnit.html#">Develop Docs</a>
 */
public interface MeasurementUnit {

  enum Duration implements MeasurementUnit {
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
    WEEK;
  }

  enum Information implements MeasurementUnit {
    /** Bit (`"bit"`), corresponding to 1/8 of a byte. */
    BIT,

    /** Byte (`"byte"`). */
    BYTE,

    /** Kilobyte (`"kilobyte"`), 10^3 bytes. */
    KILOBYTE,

    /** Kibibyte (`"kibibyte"`), 2^10 bytes. */
    KIBIBYTE,

    /** Megabyte (`"megabyte"`), 10^6 bytes. */
    MEGABYTE,

    /** Mebibyte (`"mebibyte"`), 2^20 bytes. */
    MEBIBYTE,

    /** Gigabyte (`"gigabyte"`), 10^9 bytes. */
    GIGABYTE,

    /** Gibibyte (`"gibibyte"`), 2^30 bytes. */
    GIBIBYTE,

    /** Terabyte (`"terabyte"`), 10^12 bytes. */
    TERABYTE,

    /** Tebibyte (`"tebibyte"`), 2^40 bytes. */
    TEBIBYTE,

    /** Petabyte (`"petabyte"`), 10^15 bytes. */
    PETABYTE,

    /** Pebibyte (`"pebibyte"`), 2^50 bytes. */
    PEBIBYTE,

    /** Exabyte (`"exabyte"`), 10^18 bytes. */
    EXABYTE,

    /** Exbibyte (`"exbibyte"`), 2^60 bytes. */
    EXBIBYTE;
  }

  enum Fraction implements MeasurementUnit {
    /** Floating point fraction of `1`. */
    RATIO,

    /** Ratio expressed as a fraction of `100`. `100%` equals a ratio of `1.0`. */
    PERCENT;
  }

  final class Custom implements MeasurementUnit {

    private final @NotNull String name;

    public Custom(@NotNull String name) {
      this.name = name;
    }

    @Override
    public @NotNull String name() {
      return name;
    }
  }

  @NotNull
  String name();

  @ApiStatus.Internal
  default @NotNull String apiName() {
    return name().toLowerCase(Locale.ROOT);
  }
}
