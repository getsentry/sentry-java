package io.sentry.vendor;

final class SentryMath {

  private SentryMath() {}

  static long floorDiv(final long x, final long y) {
    final long quotient = x / y;
    final long remainder = x % y;
    return remainder != 0 && hasDifferentSigns(x, y) ? quotient - 1 : quotient;
  }

  static long floorMod(final long x, final long y) {
    final long remainder = x % y;
    return remainder != 0 && hasDifferentSigns(x, y) ? remainder + y : remainder;
  }

  private static boolean hasDifferentSigns(final long x, final long y) {
    return x < 0 != y < 0;
  }
}
