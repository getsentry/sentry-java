package io.sentry.micrometer;

import io.sentry.metrics.MetricsUnit;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;

final class SentryMetricUnit {
  private SentryMetricUnit() {}

  static @Nullable String normalize(final @Nullable String unit) {
    if (unit == null) {
      return null;
    }

    switch (unit.toLowerCase(Locale.ROOT)) {
      case "ns":
      case "nanosecond":
      case "nanoseconds":
        return MetricsUnit.Duration.NANOSECOND;
      case "us":
      case "µs":
      case "microsecond":
      case "microseconds":
        return MetricsUnit.Duration.MICROSECOND;
      case "ms":
      case "millisecond":
      case "milliseconds":
        return MetricsUnit.Duration.MILLISECOND;
      case "s":
      case "sec":
      case "second":
      case "seconds":
        return MetricsUnit.Duration.SECOND;
      case "min":
      case "minute":
      case "minutes":
        return MetricsUnit.Duration.MINUTE;
      case "h":
      case "hour":
      case "hours":
        return MetricsUnit.Duration.HOUR;
      case "d":
      case "day":
      case "days":
        return MetricsUnit.Duration.DAY;
      case "w":
      case "week":
      case "weeks":
        return MetricsUnit.Duration.WEEK;
      case "bit":
      case "bits":
        return MetricsUnit.Information.BIT;
      case "b":
      case "byte":
      case "bytes":
        return MetricsUnit.Information.BYTE;
      case "kb":
      case "kilobyte":
      case "kilobytes":
        return MetricsUnit.Information.KILOBYTE;
      case "kib":
      case "kibibyte":
      case "kibibytes":
        return MetricsUnit.Information.KIBIBYTE;
      case "mb":
      case "megabyte":
      case "megabytes":
        return MetricsUnit.Information.MEGABYTE;
      case "mib":
      case "mebibyte":
      case "mebibytes":
        return MetricsUnit.Information.MEBIBYTE;
      case "gb":
      case "gigabyte":
      case "gigabytes":
        return MetricsUnit.Information.GIGABYTE;
      case "gib":
      case "gibibyte":
      case "gibibytes":
        return MetricsUnit.Information.GIBIBYTE;
      case "tb":
      case "terabyte":
      case "terabytes":
        return MetricsUnit.Information.TERABYTE;
      case "tib":
      case "tebibyte":
      case "tebibytes":
        return MetricsUnit.Information.TEBIBYTE;
      case "pb":
      case "petabyte":
      case "petabytes":
        return MetricsUnit.Information.PETABYTE;
      case "pib":
      case "pebibyte":
      case "pebibytes":
        return MetricsUnit.Information.PEBIBYTE;
      case "eb":
      case "exabyte":
      case "exabytes":
        return MetricsUnit.Information.EXABYTE;
      case "eib":
      case "exbibyte":
      case "exbibytes":
        return MetricsUnit.Information.EXBIBYTE;
      case "ratio":
        return MetricsUnit.Fraction.RATIO;
      case "%":
      case "percent":
      case "percentage":
        return MetricsUnit.Fraction.PERCENT;
      default:
        return unit;
    }
  }
}
