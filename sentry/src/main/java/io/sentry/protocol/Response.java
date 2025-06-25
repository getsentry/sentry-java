package io.sentry.protocol;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.ObjectReader;
import io.sentry.ObjectWriter;
import io.sentry.util.CollectionUtils;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Response implements JsonUnknown, JsonSerializable {
  public static final String TYPE = "response";

  /**
   * The cookie values.
   *
   * <p>Can be given unparsed as string, as dictionary, or as a list of tuples.
   */
  private @Nullable String cookies;

  /**
   * A dictionary of response headers.
   *
   * <p>If a header appears multiple times it, needs to be merged according to the HTTP standard for
   * header merging. Header names are treated case-insensitively by Sentry.
   */
  private @Nullable Map<String, String> headers;

  /** The HTTP response status code */
  private @Nullable Integer statusCode;

  /** The body size in bytes */
  private @Nullable Long bodySize;

  /**
   * Response data in any format that makes sense.
   *
   * <p>SDKs should discard large and binary bodies by default. Can be given as a string or
   * structural data of any format.
   */
  private @Nullable Object data;

  @SuppressWarnings("unused")
  private @Nullable Map<String, Object> unknown;

  public Response() {}

  public Response(final @NotNull Response response) {
    this.cookies = response.cookies;
    this.headers = CollectionUtils.newConcurrentHashMap(response.headers);
    this.unknown = CollectionUtils.newConcurrentHashMap(response.unknown);
    this.statusCode = response.statusCode;
    this.bodySize = response.bodySize;
    this.data = response.data;
  }

  public @Nullable String getCookies() {
    return cookies;
  }

  public void setCookies(final @Nullable String cookies) {
    this.cookies = cookies;
  }

  public @Nullable Map<String, String> getHeaders() {
    return headers;
  }

  public void setHeaders(final @Nullable Map<String, String> headers) {
    this.headers = CollectionUtils.newConcurrentHashMap(headers);
  }

  @Nullable
  @Override
  public Map<String, Object> getUnknown() {
    return unknown;
  }

  @Override
  public void setUnknown(final @Nullable Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  public @Nullable Integer getStatusCode() {
    return statusCode;
  }

  public void setStatusCode(final @Nullable Integer statusCode) {
    this.statusCode = statusCode;
  }

  public @Nullable Long getBodySize() {
    return bodySize;
  }

  public void setBodySize(final @Nullable Long bodySize) {
    this.bodySize = bodySize;
  }

  public @Nullable Object getData() {
    return data;
  }

  public void setData(final @Nullable Object data) {
    this.data = data;
  }

  // region json

  public static final class JsonKeys {
    public static final String COOKIES = "cookies";
    public static final String HEADERS = "headers";
    public static final String STATUS_CODE = "status_code";
    public static final String BODY_SIZE = "body_size";
    public static final String DATA = "data";
  }

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();

    if (cookies != null) {
      writer.name(JsonKeys.COOKIES).value(cookies);
    }
    if (headers != null) {
      writer.name(JsonKeys.HEADERS).value(logger, headers);
    }
    if (statusCode != null) {
      writer.name(JsonKeys.STATUS_CODE).value(logger, statusCode);
    }
    if (bodySize != null) {
      writer.name(JsonKeys.BODY_SIZE).value(logger, bodySize);
    }
    if (data != null) {
      writer.name(JsonKeys.DATA).value(logger, data);
    }
    if (unknown != null) {
      for (final String key : unknown.keySet()) {
        final Object value = unknown.get(key);
        writer.name(key);
        writer.value(logger, value);
      }
    }
    writer.endObject();
  }

  @SuppressWarnings("unchecked")
  public static final class Deserializer implements JsonDeserializer<Response> {
    @Override
    public @NotNull Response deserialize(
        final @NotNull ObjectReader reader, final @NotNull ILogger logger) throws Exception {
      reader.beginObject();
      final Response response = new Response();
      Map<String, Object> unknown = null;
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.COOKIES:
            response.cookies = reader.nextStringOrNull();
            break;
          case JsonKeys.HEADERS:
            final Map<String, String> deserializedHeaders =
                (Map<String, String>) reader.nextObjectOrNull();
            if (deserializedHeaders != null) {
              response.headers = CollectionUtils.newConcurrentHashMap(deserializedHeaders);
            }
            break;
          case JsonKeys.STATUS_CODE:
            response.statusCode = reader.nextIntegerOrNull();
            break;
          case JsonKeys.BODY_SIZE:
            response.bodySize = reader.nextLongOrNull();
            break;
          case JsonKeys.DATA:
            response.data = reader.nextObjectOrNull();
            break;
          default:
            if (unknown == null) {
              unknown = new ConcurrentHashMap<>();
            }
            reader.nextUnknown(logger, unknown, nextName);
            break;
        }
      }
      response.setUnknown(unknown);
      reader.endObject();
      return response;
    }
  }

  // endregion
}
