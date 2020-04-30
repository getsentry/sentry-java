package io.sentry.core.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class StringUtils {

  private StringUtils() {}

  public static @Nullable String getStringAfterDot(final @Nullable String str) {
    if (str != null) {
      final int lastDotIndex = str.lastIndexOf(".");
      if (lastDotIndex >= 0 && str.length() > (lastDotIndex + 1)) {
        return str.substring(lastDotIndex + 1);
      } else {
        return str;
      }
    }
    return null;
  }
}
