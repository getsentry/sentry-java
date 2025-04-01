package io.sentry.util;

import io.sentry.ILogger;
import io.sentry.SentryLevel;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.Iterator;
import java.util.Locale;
import java.util.regex.Pattern;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class StringUtils {

  private static final Charset UTF_8 = Charset.forName("UTF-8");

  public static final String PROPER_NIL_UUID = "00000000-0000-0000-0000-000000000000";
  private static final String CORRUPTED_NIL_UUID = "0000-0000";
  private static final @NotNull Pattern PATTERN_WORD_SNAKE_CASE = Pattern.compile("[\\W_]+");

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
   * Converts a String to CamelCase format. E.g. metric_bucket => MetricBucket;
   *
   * @param str the String to convert
   * @return the camel case converted String or itself if empty or null
   */
  public static @Nullable String camelCase(final @Nullable String str) {
    if (str == null || str.isEmpty()) {
      return str;
    }

    String[] words = PATTERN_WORD_SNAKE_CASE.split(str, -1);
    StringBuilder builder = new StringBuilder();
    for (String w : words) {
      builder.append(capitalize(w));
    }
    return builder.toString();
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
    return String.format(Locale.ROOT, "%.1f %cB", bytes / 1000.0, ci.current());
  }

  /**
   * Calculates the SHA-1 String hash
   *
   * @param str the String
   * @param logger the Logger
   * @return The hashed String or null otherwise
   */
  public static @Nullable String calculateStringHash(
      final @Nullable String str, final @NotNull ILogger logger) {
    if (str == null || str.isEmpty()) {
      return null;
    }

    try {
      // getInstance() method is called with algorithm SHA-1
      final MessageDigest md = MessageDigest.getInstance("SHA-1");

      // digest() method is called
      // to calculate message digest of the input string
      // returned as array of byte
      final byte[] messageDigest = md.digest(str.getBytes(UTF_8));

      // Convert byte array into signum representation
      final BigInteger no = new BigInteger(1, messageDigest);

      // Convert message digest into hex value
      final StringBuilder stringBuilder = new StringBuilder(no.toString(16));

      // return the HashText
      return stringBuilder.toString();
    }

    // For specifying wrong message digest algorithms
    catch (NoSuchAlgorithmException e) {
      if (logger.isEnabled(SentryLevel.INFO)) {
        logger.log(SentryLevel.INFO, "SHA-1 isn't available to calculate the hash.", e);
      }
    } catch (Throwable e) {
      if (logger.isEnabled(SentryLevel.INFO)) {
        logger.log(SentryLevel.INFO, "string: %s could not calculate its hash", e, str);
      }
    }
    return null;
  }

  /**
   * Counts the occurrences of a character in a String
   *
   * @param str the String
   * @param character the character to count
   * @return The number of occurrences of the character in the String
   */
  public static int countOf(@NotNull String str, char character) {
    int count = 0;
    for (int i = 0; i < str.length(); i++) {
      if (str.charAt(i) == character) {
        count++;
      }
    }
    return count;
  }

  /**
   * Normalizes UUID string representation to adhere to the actual UUID standard
   *
   * <p>Because Motorola decided that nil UUIDs should look like this: "0000-0000" ;)
   *
   * @param uuidString the original UUID string representation
   * @return proper UUID string, in case it's a corrupted one
   */
  public static String normalizeUUID(final @NotNull String uuidString) {
    if (uuidString.equals(CORRUPTED_NIL_UUID)) {
      return PROPER_NIL_UUID;
    }
    return uuidString;
  }

  /**
   * Returns a new String joining together given strings using the given delimiter.
   *
   * @param delimiter the delimiter that separates elements
   * @param elements the elements that should be joined together
   * @return a new String with elements joined using delimiter
   */
  public static String join(
      final @NotNull CharSequence delimiter,
      final @NotNull Iterable<? extends CharSequence> elements) {
    final @NotNull StringBuilder stringBuilder = new StringBuilder();
    final @NotNull Iterator<? extends CharSequence> iterator = elements.iterator();

    if (iterator.hasNext()) {
      stringBuilder.append(iterator.next());
      while (iterator.hasNext()) {
        stringBuilder.append(delimiter);
        stringBuilder.append(iterator.next());
      }
    }

    return stringBuilder.toString();
  }

  public static @Nullable String toString(final @Nullable Object object) {
    if (object == null) {
      return null;
    }

    return object.toString();
  }

  public static @NotNull String removePrefix(
      final @Nullable String string, final @NotNull String prefix) {
    if (string == null) {
      return "";
    }
    final int index = string.indexOf(prefix);
    if (index == 0) {
      return string.substring(prefix.length());
    } else {
      return string;
    }
  }

  public static @NotNull String substringBefore(
      final @Nullable String string, final @NotNull String separator) {
    if (string == null) {
      return "";
    }
    final int index = string.indexOf(separator);
    if (index >= 0) {
      return string.substring(0, index);
    } else {
      return string;
    }
  }
}
