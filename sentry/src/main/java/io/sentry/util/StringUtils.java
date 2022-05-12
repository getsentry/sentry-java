package io.sentry.util;

import io.sentry.ILogger;
import io.sentry.SentryLevel;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.Locale;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class StringUtils {

  private static final Charset UTF_8 = Charset.forName("UTF-8");

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

      // Add preceding 0s to make it 32 bit
      while (stringBuilder.length() < 32) {
        stringBuilder.insert(0, "0");
      }

      // return the HashText
      return stringBuilder.toString();
    }

    // For specifying wrong message digest algorithms
    catch (NoSuchAlgorithmException e) {
      logger.log(SentryLevel.INFO, "SHA-1 isn't available to calculate the hash.", e);
    } catch (Throwable e) {
      logger.log(SentryLevel.INFO, "string: %s could not calculate its hash", e, str);
    }
    return null;
  }
}
