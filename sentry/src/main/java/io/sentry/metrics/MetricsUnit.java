package io.sentry.metrics;

/**
 * String constants for metric units.
 *
 * <p>These constants represent the API names of measurement units that can be used with metrics.
 */
public final class MetricsUnit {

  /** Time duration units. */
  public static final class Duration {
    /** Nanosecond, 10^-9 seconds. */
    public static final String NANOSECOND = "nanosecond";

    /** Microsecond, 10^-6 seconds. */
    public static final String MICROSECOND = "microsecond";

    /** Millisecond, 10^-3 seconds. */
    public static final String MILLISECOND = "millisecond";

    /** Full second. */
    public static final String SECOND = "second";

    /** Minute, 60 seconds. */
    public static final String MINUTE = "minute";

    /** Hour, 3600 seconds. */
    public static final String HOUR = "hour";

    /** Day, 86,400 seconds. */
    public static final String DAY = "day";

    /** Week, 604,800 seconds. */
    public static final String WEEK = "week";

    private Duration() {}
  }

  /** Size of information derived from bytes. */
  public static final class Information {
    /** Bit, corresponding to 1/8 of a byte. */
    public static final String BIT = "bit";

    /** Byte. */
    public static final String BYTE = "byte";

    /** Kilobyte, 10^3 bytes. */
    public static final String KILOBYTE = "kilobyte";

    /** Kibibyte, 2^10 bytes. */
    public static final String KIBIBYTE = "kibibyte";

    /** Megabyte, 10^6 bytes. */
    public static final String MEGABYTE = "megabyte";

    /** Mebibyte, 2^20 bytes. */
    public static final String MEBIBYTE = "mebibyte";

    /** Gigabyte, 10^9 bytes. */
    public static final String GIGABYTE = "gigabyte";

    /** Gibibyte, 2^30 bytes. */
    public static final String GIBIBYTE = "gibibyte";

    /** Terabyte, 10^12 bytes. */
    public static final String TERABYTE = "terabyte";

    /** Tebibyte, 2^40 bytes. */
    public static final String TEBIBYTE = "tebibyte";

    /** Petabyte, 10^15 bytes. */
    public static final String PETABYTE = "petabyte";

    /** Pebibyte, 2^50 bytes. */
    public static final String PEBIBYTE = "pebibyte";

    /** Exabyte, 10^18 bytes. */
    public static final String EXABYTE = "exabyte";

    /** Exbibyte, 2^60 bytes. */
    public static final String EXBIBYTE = "exbibyte";

    private Information() {}
  }

  /** Fractions such as percentages. */
  public static final class Fraction {
    /** Floating point fraction of `1`. */
    public static final String RATIO = "ratio";

    /** Ratio expressed as a fraction of `100`. `100%` equals a ratio of `1.0`. */
    public static final String PERCENT = "percent";

    private Fraction() {}
  }

  private MetricsUnit() {}
}
