package io.sentry.core.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class IntegerUtils {

  private IntegerUtils() {}

  /**
   * Try to parse a String to a Number
   *
   * @param number the number String
   * @return the Integer or null
   */
  public static @Nullable Integer getNumber(final @Nullable String number) {
    if (number == null || number.isEmpty()) {
      return null;
    }
    try {
      return Integer.valueOf(number);
    } catch (NumberFormatException ignored) {
      // number String is not a number
    }
    return null;
  }
}
