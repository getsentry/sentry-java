package io.sentry.util.network;

import io.sentry.ILogger;
import io.sentry.SentryLevel;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Utility class for parsing and creating NetworkBody instances. */
public final class NetworkBodyParser {

  private NetworkBodyParser() {}

  /**
   * Creates a NetworkBody from raw bytes with content type information. This is useful for handling
   * binary or unknown content types.
   *
   * @param bytes The raw bytes of the body
   * @param contentType Optional content type hint to help with parsing
   * @param charset Optional charset to use for text conversion (defaults to UTF-8)
   * @param maxSizeBytes Maximum size to process
   * @param logger Optional logger
   * @return A NetworkBody or null if bytes are null/empty
   */
  public static @Nullable NetworkBody fromBytes(
      @Nullable final byte[] bytes,
      @Nullable final String contentType,
      @Nullable final String charset,
      final int maxSizeBytes,
      @NotNull final ILogger logger) {

    if (bytes == null || bytes.length == 0) {
      return null;
    }

    if (contentType != null && isBinaryContentType(contentType)) {
      // For binary content, return a description instead of the actual content
      return NetworkBody.fromString(
          "[Binary data, " + bytes.length + " bytes, type: " + contentType + "]");
    }

    // Check size limit and truncate if necessary
    if (bytes.length > maxSizeBytes) {
      logger.log(
          SentryLevel.WARNING, "Content exceeds max size limit of " + maxSizeBytes + " bytes");
      return createTruncatedNetworkBody(bytes, maxSizeBytes, charset);
    }

    // Convert to string and parse
    try {
      String effectiveCharset = charset != null ? charset : "UTF-8";
      String content = new String(bytes, effectiveCharset);
      return parse(content, contentType, logger);
    } catch (UnsupportedEncodingException e) {
      logger.log(SentryLevel.WARNING, "Failed to decode bytes: " + e.getMessage());
      return NetworkBody.fromString("[Failed to decode bytes, " + bytes.length + " bytes]");
    }
  }

  private static @Nullable NetworkBody parse(
      @Nullable final String content,
      @Nullable final String contentType,
      @NotNull final ILogger logger) {

    if (content == null || content.isEmpty()) {
      return null;
    }

    try {
      // TODO content-type can contain actual encoding, e.g. "application/json; charset=utf-8"
      if (contentType != null) {
        final @NotNull String lowerContentType = contentType.toLowerCase(Locale.ROOT);

        if (lowerContentType.equals("application/x-www-form-urlencoded")) {
          return parseFormUrlEncoded(content, logger);
        }

        if (lowerContentType.contains("application/json")) {
          return NetworkBody.fromRawJson(content);
        }
      }
    } catch (Throwable t) {
      logger.log(SentryLevel.WARNING, "Failed to perform content type network body parsing", t);
    }
    return NetworkBody.fromString(content);
  }

  /** Parses URL-encoded form data into a JsonObject NetworkBody. */
  private static @Nullable NetworkBody parseFormUrlEncoded(
      @NotNull final String content, @Nullable final ILogger logger) {
    try {
      Map<String, Object> params = new HashMap<>();
      String[] pairs = content.split("&", -1);

      for (String pair : pairs) {
        int idx = pair.indexOf("=");
        if (idx > 0) {
          String key = URLDecoder.decode(pair.substring(0, idx), "UTF-8");
          String value =
              idx < pair.length() - 1 ? URLDecoder.decode(pair.substring(idx + 1), "UTF-8") : "";

          // Handle multiple values for the same key
          if (params.containsKey(key)) {
            Object existing = params.get(key);
            if (existing instanceof List) {
              @SuppressWarnings("unchecked")
              List<String> list = (List<String>) existing;
              list.add(value);
            } else {
              List<String> list = new ArrayList<>();
              list.add((String) existing);
              list.add(value);
              params.put(key, list);
            }
          } else {
            params.put(key, value);
          }
        }
      }

      return NetworkBody.fromJsonObject(params);
    } catch (UnsupportedEncodingException e) {
      if (logger != null) {
        logger.log(SentryLevel.WARNING, "Failed to parse form data: " + e.getMessage());
      }
      return null;
    }
  }

  /** Creates a truncated NetworkBody from oversized bytes with proper UTF-8 character handling. */
  private static @NotNull NetworkBody createTruncatedNetworkBody(
      @NotNull final byte[] bytes, final int maxSizeBytes, @Nullable final String charset) {
    byte[] truncatedBytes = new byte[maxSizeBytes];
    System.arraycopy(bytes, 0, truncatedBytes, 0, maxSizeBytes);

    try {
      String effectiveCharset = charset != null ? charset : "UTF-8";
      String content = new String(truncatedBytes, effectiveCharset);

      // Find the last complete character by checking for replacement character
      int lastValidIndex = content.length();
      while (lastValidIndex > 0 && content.charAt(lastValidIndex - 1) == '\uFFFD') {
        lastValidIndex--;
      }
      if (lastValidIndex < content.length()) {
        content = content.substring(0, lastValidIndex);
      }
      content += "...[truncated]";
      return NetworkBody.fromString(content);
    } catch (UnsupportedEncodingException e) {
      return NetworkBody.fromString(
          "[Failed to decode truncated bytes, " + bytes.length + " bytes]");
    }
  }

  /** Checks if the content type is binary and shouldn't be converted to string. */
  private static boolean isBinaryContentType(@NotNull final String contentType) {
    final @NotNull String lower = contentType.toLowerCase(Locale.ROOT);
    return lower.contains("image/")
        || lower.contains("video/")
        || lower.contains("audio/")
        || lower.contains("application/octet-stream")
        || lower.contains("application/pdf")
        || lower.contains("application/zip")
        || lower.contains("application/gzip");
  }
}
