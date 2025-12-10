package io.sentry.util.network;

import io.sentry.ILogger;
import io.sentry.SentryLevel;
import io.sentry.vendor.gson.stream.JsonReader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Utility class for parsing and creating NetworkBody instances. */
@ApiStatus.Internal
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
      return new NetworkBody(
          "[Binary data, " + bytes.length + " bytes, type: " + contentType + "]");
    }

    final boolean isPartial = bytes.length >= maxSizeBytes;

    // Convert to string and parse
    try {
      final String effectiveCharset = charset != null ? charset : "UTF-8";
      final String content = new String(bytes, effectiveCharset);
      return parse(content, contentType, isPartial, logger);
    } catch (UnsupportedEncodingException e) {
      logger.log(SentryLevel.WARNING, "Failed to decode bytes: " + e.getMessage());
      return new NetworkBody(
          "[Failed to decode bytes, " + bytes.length + " bytes]",
          Collections.singletonList(NetworkBody.NetworkBodyWarning.BODY_PARSE_ERROR));
    }
  }

  private static @Nullable NetworkBody parse(
      @Nullable final String content,
      @Nullable final String contentType,
      final boolean isPartial,
      @Nullable final ILogger logger) {

    if (content == null || content.isEmpty()) {
      return null;
    }

    // Handle based on content type hint if provided
    if (contentType != null) {
      final @NotNull String lowerContentType = contentType.toLowerCase(Locale.ROOT);
      if (lowerContentType.contains("application/x-www-form-urlencoded")) {
        return parseFormUrlEncoded(content, isPartial, logger);
      } else if (lowerContentType.contains("application/json")) {
        return parseJson(content, isPartial, logger);
      }
    }

    // Default to string representation
    final List<NetworkBody.NetworkBodyWarning> warnings =
        isPartial ? Collections.singletonList(NetworkBody.NetworkBodyWarning.TEXT_TRUNCATED) : null;
    return new NetworkBody(content, warnings);
  }

  @NotNull
  private static NetworkBody parseJson(
      final @NotNull String content, final boolean isPartial, final @Nullable ILogger logger) {
    try (final JsonReader reader = new JsonReader(new StringReader(content))) {
      final @Nullable Object data = readJsonSafely(reader);
      if (data != null) {
        final @Nullable List<NetworkBody.NetworkBodyWarning> warnings;
        if (isPartial) {
          warnings = Collections.singletonList(NetworkBody.NetworkBodyWarning.JSON_TRUNCATED);
        } else {
          warnings = null;
        }
        return new NetworkBody(data, warnings);
      }
    } catch (Exception e) {
      if (logger != null) {
        logger.log(SentryLevel.WARNING, "Failed to parse JSON: " + e.getMessage());
      }
    }
    return new NetworkBody(
        null, Collections.singletonList(NetworkBody.NetworkBodyWarning.INVALID_JSON));
  }

  /** Parses URL-encoded form data into a JsonObject NetworkBody. */
  @NotNull
  private static NetworkBody parseFormUrlEncoded(
      @NotNull final String content, final boolean isPartial, @Nullable final ILogger logger) {
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
      final List<NetworkBody.NetworkBodyWarning> warnings;
      if (isPartial) {
        warnings = Collections.singletonList(NetworkBody.NetworkBodyWarning.TEXT_TRUNCATED);
      } else {
        warnings = null;
      }
      return new NetworkBody(params, warnings);
    } catch (UnsupportedEncodingException e) {
      if (logger != null) {
        logger.log(SentryLevel.WARNING, "Failed to parse form data: " + e.getMessage());
      }
    }
    return new NetworkBody(
        null, Collections.singletonList(NetworkBody.NetworkBodyWarning.BODY_PARSE_ERROR));
  }

  /** Checks if the content type is binary and shouldn't be converted to string. */
  private static boolean isBinaryContentType(@NotNull final String contentType) {
    String lower = contentType.toLowerCase();
    return lower.contains("image/")
        || lower.contains("video/")
        || lower.contains("audio/")
        || lower.contains("application/octet-stream")
        || lower.contains("application/pdf")
        || lower.contains("application/zip")
        || lower.contains("application/gzip");
  }

  @Nullable
  private static Object readJsonSafely(final @NotNull JsonReader reader) {
    try {
      switch (reader.peek()) {
        case BEGIN_OBJECT:
          final @NotNull Map<String, Object> map = new LinkedHashMap<>();
          reader.beginObject();
          try {
            while (reader.hasNext()) {
              try {
                String name = reader.nextName();
                map.put(name, readJsonSafely(reader)); // recursive call
              } catch (Exception e) {
                // ignored
              }
            }
            reader.endObject();
          } catch (Exception e) {
            // ignored
          }
          return map;

        case BEGIN_ARRAY:
          final List<Object> list = new ArrayList<>();
          reader.beginArray();
          try {
            while (reader.hasNext()) {
              list.add(readJsonSafely(reader)); // recursive call
            }
            reader.endArray();
          } catch (Exception e) {
            // ignored
          }

          return list;

        case STRING:
          return reader.nextString();

        case NUMBER:
          // You can customize number handling (int, long, double) here
          return reader.nextDouble();

        case BOOLEAN:
          return reader.nextBoolean();

        case NULL:
          reader.nextNull();
          return null;

        default:
          throw new IllegalStateException("Unexpected JSON token: " + reader.peek());
      }
    } catch (Exception e) {
      return null;
    }
  }
}
