package io.sentry.util;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.Locale;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
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

  /**
   * Returns a Capitalized String and all remaining chars to lower case. eg seSSioN = Session
   *
   * @param str the String to capitalize
   * @return the capitalized String or itself if empty or null
   */
  public static @Nullable String capitalize(final @Nullable String str) {
    if (str == null || str.isEmpty()) {
      return str;
    }

    return str.substring(0, 1).toUpperCase(Locale.ROOT) + str.substring(1).toLowerCase(Locale.ROOT);
  }

  /**
   * Removes character specified by the delimiter parameter from the beginning and the end of the
   * string.
   *
   * @param str the String to remove surrounding string from
   * @param delimiter the String that is meant to be removed
   * @return a string without delimiter character at the beginning and the end of the string
   */
  public static @Nullable String removeSurrounding(
      @Nullable final String str, @Nullable final String delimiter) {
    if (str != null && delimiter != null && str.startsWith(delimiter) && str.endsWith(delimiter)) {
      return str.substring(delimiter.length(), str.length() - delimiter.length());
    } else {
      return str;
    }
  }

  /**
   * Converts the given number of bytes to a human-readable string.
   *
   * @param bytes the number of bytes
   * @return a string representing the human-readable byte count (e.g. 1kB, 20 MB, etc.)
   */
  public static @NotNull String byteCountToString(long bytes) {
    if (-1000 < bytes && bytes < 1000) {
      return bytes + " B";
    }
    CharacterIterator ci = new StringCharacterIterator("kMGTPE");
    while (bytes <= -999_950 || bytes >= 999_950) {
      bytes /= 1000;
      ci.next();
    }
    return String.format(Locale.US, "%.1f %cB", bytes / 1000.0, ci.current());
  }
}
