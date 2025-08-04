package io.sentry.protocol;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.ObjectReader;
import io.sentry.ObjectWriter;
import io.sentry.util.CollectionUtils;
import io.sentry.util.Objects;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Http request information.
 *
 * <p>The Request interface contains information on a HTTP request related to the event. In client
 * SDKs, this can be an outgoing request, or the request that rendered the current web page. On
 * server SDKs, this could be the incoming web request that is being handled.
 *
 * <p>The data variable should only contain the request body (not the query string). It can either
 * be a dictionary (for standard HTTP requests) or a raw request body.
 *
 * <p>### Ordered Maps
 *
 * <p>In the Request interface, several attributes can either be declared as string, object, or list
 * of tuples. Sentry attempts to parse structured information from the string representation in such
 * cases.
 *
 * <p>Sometimes, keys can be declared multiple times, or the order of elements matters. In such
 * cases, use the tuple representation over a plain object.
 *
 * <p>Example of request headers as object:
 *
 * <p>```json { "content-type": "application/json", "accept": "application/json, application/xml" }
 * ```
 *
 * <p>Example of the same headers as list of tuples:
 *
 * <p>```json [ ["content-type", "application/json"], ["accept", "application/json"], ["accept",
 * "application/xml"] ] ```
 *
 * <p>Example of a fully populated request object:
 *
 * <p>```json { "request": { "method": "POST", "url": "http://absolute.uri/foo", "query_string":
 * "query=foobar", "data": { "foo": "bar" }, "cookies": "PHPSESSID=298zf09hf012fh2;
 * csrftoken=u32t4o3tb3gg43; _gat=1;", "headers": { "content-type": "text/html" }, "env": {
 * "REMOTE_ADDR": "192.168.0.1" } } } ```
 */
public final class Request implements JsonUnknown, JsonSerializable {
  /**
   * The URL of the request if available.
   *
   * <p>The query string can be declared either as part of the `url`, or separately in
   * `query_string`.
   */
  private @Nullable String url;

  /** HTTP request method. */
  private @Nullable String method;

  /**
   * The query string component of the URL.
   *
   * <p>Can be given as unparsed string, dictionary, or list of tuples. (Currently only unparsed
   * string is possible)
   *
   * <p>If the query string is not declared and part of the `url`, Sentry moves it to the query
   * string.
   */
  private @Nullable String queryString;

  /**
   * Request data in any format that makes sense.
   *
   * <p>SDKs should discard large and binary bodies by default. Can be given as string or structural
   * data of any serializable format.
   */
  private @Nullable Object data;

  /**
   * The cookie values.
   *
   * <p>Can be given unparsed as string, as dictionary, or as a list of tuples.
   */
  private @Nullable String cookies;

  /**
   * A dictionary of submitted headers.
   *
   * <p>If a header appears multiple times it, needs to be merged according to the HTTP standard for
   * header merging. Header names are treated case-insensitively by Sentry.
   */
  private @Nullable Map<String, String> headers;

  /**
   * Server environment data, such as CGI/WSGI.
   *
   * <p>A dictionary containing environment information passed from the server. This is where
   * information such as CGI/WSGI/Rack keys go that are not HTTP headers.
   *
   * <p>Sentry will explicitly look for `REMOTE_ADDR` to extract an IP address.
   */
  private @Nullable Map<String, String> env;

  /** The body size in bytes */
  private @Nullable Long bodySize;

  private @Nullable Map<String, String> other;

  /** The fragment (anchor) of the request URL. */
  private @Nullable String fragment;

  /**
   * The API target/specification that made the request.
   *
   * <p>Values can be `graphql`, `rest`, etc.
   *
   * <p>The data field should contain the request and response bodies based on its target
   * specification.
   */
  private @Nullable String apiTarget;

  @SuppressWarnings("unused")
  private @Nullable Map<String, Object> unknown;

  public Request() {}

  public Request(final @NotNull Request request) {
    this.url = request.url;
    this.cookies = request.cookies;
    this.method = request.method;
    this.queryString = request.queryString;
    this.headers = CollectionUtils.newConcurrentHashMap(request.headers);
    this.env = CollectionUtils.newConcurrentHashMap(request.env);
    this.other = CollectionUtils.newConcurrentHashMap(request.other);
    this.unknown = CollectionUtils.newConcurrentHashMap(request.unknown);
    this.data = request.data;
    this.fragment = request.fragment;
    this.bodySize = request.bodySize;
    this.apiTarget = request.apiTarget;
  }

  public @Nullable String getUrl() {
    return url;
  }

  public void setUrl(final @Nullable String url) {
    this.url = url;
  }

  public @Nullable String getMethod() {
    return method;
  }

  public void setMethod(final @Nullable String method) {
    this.method = method;
  }

  public @Nullable String getQueryString() {
    return queryString;
  }

  public void setQueryString(final @Nullable String queryString) {
    this.queryString = queryString;
  }

  public @Nullable Object getData() {
    return data;
  }

  public void setData(final @Nullable Object data) {
    this.data = data;
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

  public @Nullable Map<String, String> getEnvs() {
    return env;
  }

  public void setEnvs(final @Nullable Map<String, String> env) {
    this.env = CollectionUtils.newConcurrentHashMap(env);
  }

  public @Nullable Map<String, String> getOthers() {
    return other;
  }

  public void setOthers(final @Nullable Map<String, String> other) {
    this.other = CollectionUtils.newConcurrentHashMap(other);
  }

  public @Nullable String getFragment() {
    return fragment;
  }

  public void setFragment(final @Nullable String fragment) {
    this.fragment = fragment;
  }

  public @Nullable Long getBodySize() {
    return bodySize;
  }

  public void setBodySize(final @Nullable Long bodySize) {
    this.bodySize = bodySize;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Request request = (Request) o;
    return Objects.equals(url, request.url)
        && Objects.equals(method, request.method)
        && Objects.equals(queryString, request.queryString)
        && Objects.equals(cookies, request.cookies)
        && Objects.equals(headers, request.headers)
        && Objects.equals(env, request.env)
        && Objects.equals(bodySize, request.bodySize)
        && Objects.equals(fragment, request.fragment)
        && Objects.equals(apiTarget, request.apiTarget);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        url, method, queryString, cookies, headers, env, bodySize, fragment, apiTarget);
  }

  // region json

  @Nullable
  @Override
  public Map<String, Object> getUnknown() {
    return unknown;
  }

  @Override
  public void setUnknown(@Nullable Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  public @Nullable String getApiTarget() {
    return apiTarget;
  }

  public void setApiTarget(final @Nullable String apiTarget) {
    this.apiTarget = apiTarget;
  }

  public static final class JsonKeys {
    public static final String URL = "url";
    public static final String METHOD = "method";
    public static final String QUERY_STRING = "query_string";
    public static final String DATA = "data";
    public static final String COOKIES = "cookies";
    public static final String HEADERS = "headers";
    public static final String ENV = "env";
    public static final String OTHER = "other";
    public static final String FRAGMENT = "fragment";
    public static final String BODY_SIZE = "body_size";
    public static final String API_TARGET = "api_target";
  }

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    if (url != null) {
      writer.name(JsonKeys.URL).value(url);
    }
    if (method != null) {
      writer.name(JsonKeys.METHOD).value(method);
    }
    if (queryString != null) {
      writer.name(JsonKeys.QUERY_STRING).value(queryString);
    }
    if (data != null) {
      writer.name(JsonKeys.DATA).value(logger, data);
    }
    if (cookies != null) {
      writer.name(JsonKeys.COOKIES).value(cookies);
    }
    if (headers != null) {
      writer.name(JsonKeys.HEADERS).value(logger, headers);
    }
    if (env != null) {
      writer.name(JsonKeys.ENV).value(logger, env);
    }
    if (other != null) {
      writer.name(JsonKeys.OTHER).value(logger, other);
    }
    if (fragment != null) {
      writer.name(JsonKeys.FRAGMENT).value(logger, fragment);
    }
    if (bodySize != null) {
      writer.name(JsonKeys.BODY_SIZE).value(logger, bodySize);
    }
    if (apiTarget != null) {
      writer.name(JsonKeys.API_TARGET).value(logger, apiTarget);
    }
    if (unknown != null) {
      for (String key : unknown.keySet()) {
        Object value = unknown.get(key);
        writer.name(key);
        writer.value(logger, value);
      }
    }
    writer.endObject();
  }

  @SuppressWarnings("unchecked")
  public static final class Deserializer implements JsonDeserializer<Request> {
    @Override
    public @NotNull Request deserialize(@NotNull ObjectReader reader, @NotNull ILogger logger)
        throws Exception {
      reader.beginObject();
      Request request = new Request();
      Map<String, Object> unknown = null;
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.URL:
            request.url = reader.nextStringOrNull();
            break;
          case JsonKeys.METHOD:
            request.method = reader.nextStringOrNull();
            break;
          case JsonKeys.QUERY_STRING:
            request.queryString = reader.nextStringOrNull();
            break;
          case JsonKeys.DATA:
            request.data = reader.nextObjectOrNull();
            break;
          case JsonKeys.COOKIES:
            request.cookies = reader.nextStringOrNull();
            break;
          case JsonKeys.HEADERS:
            Map<String, String> deserializedHeaders =
                (Map<String, String>) reader.nextObjectOrNull();
            if (deserializedHeaders != null) {
              request.headers = CollectionUtils.newConcurrentHashMap(deserializedHeaders);
            }
            break;
          case JsonKeys.ENV:
            Map<String, String> deserializedEnv = (Map<String, String>) reader.nextObjectOrNull();
            if (deserializedEnv != null) {
              request.env = CollectionUtils.newConcurrentHashMap(deserializedEnv);
            }
            break;
          case JsonKeys.OTHER:
            Map<String, String> deserializedOther = (Map<String, String>) reader.nextObjectOrNull();
            if (deserializedOther != null) {
              request.other = CollectionUtils.newConcurrentHashMap(deserializedOther);
            }
            break;
          case JsonKeys.FRAGMENT:
            request.fragment = reader.nextStringOrNull();
            break;
          case JsonKeys.BODY_SIZE:
            request.bodySize = reader.nextLongOrNull();
            break;
          case JsonKeys.API_TARGET:
            request.apiTarget = reader.nextStringOrNull();
            break;
          default:
            if (unknown == null) {
              unknown = new ConcurrentHashMap<>();
            }
            reader.nextUnknown(logger, unknown, nextName);
            break;
        }
      }
      request.setUnknown(unknown);
      reader.endObject();
      return request;
    }
  }

  // endregion
}
