package io.sentry.util;

import java.util.Set;
import org.jetbrains.annotations.NotNull;

/** Utility class for pattern matching operations, primarily used for Session Replay masking. */
public final class PatternUtils {

  private PatternUtils() {}

  /**
   * Checks if a given string matches a pattern. The pattern can contain wildcards (*) only at the
   * end to match any sequence of characters as a suffix.
   *
   * @param input the string to check
   * @param pattern the pattern to match against (only suffix wildcards are supported)
   * @return true if the input matches the pattern, false otherwise
   */
  public static boolean matchesPattern(final @NotNull String input, final @NotNull String pattern) {
    // If pattern doesn't contain wildcard, do exact match
    if (!pattern.contains("*")) {
      return input.equals(pattern);
    }

    // Only support suffix wildcards (pattern ending with *)
    if (!pattern.endsWith("*")) {
      return false;
    }

    // Check if pattern has wildcards in the middle or beginning (not supported)
    final String prefix = pattern.substring(0, pattern.length() - 1);
    if (prefix.contains("*")) {
      return false;
    }

    // Check if input starts with the prefix
    return input.startsWith(prefix);
  }

  /**
   * Checks if a given string matches any of the provided patterns. Patterns can contain wildcards
   * (*) only at the end to match any sequence of characters as a suffix.
   *
   * @param input the string to check
   * @param patterns the set of patterns to match against
   * @return true if the input matches any of the patterns, false otherwise
   */
  public static boolean matchesAnyPattern(
      final @NotNull String input, final @NotNull Set<String> patterns) {
    for (final String pattern : patterns) {
      if (matchesPattern(input, pattern)) {
        return true;
      }
    }
    return false;
  }
}
