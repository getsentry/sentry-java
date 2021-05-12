package io.sentry.protocol;

import io.sentry.IUnknownPropertiesConsumer;
import io.sentry.util.CollectionUtils;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

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
public final class Request implements Cloneable, IUnknownPropertiesConsumer {
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

  private @Nullable Map<String, String> other;

  @SuppressWarnings("unused")
  private @Nullable Map<String, Object> unknown;

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
    this.headers = headers != null ? new ConcurrentHashMap<>(headers) : null;
  }

  public @Nullable Map<String, String> getEnvs() {
    return env;
  }

  public void setEnvs(final @Nullable Map<String, String> env) {
    this.env = env != null ? new ConcurrentHashMap<>(env) : null;
  }

  public @Nullable Map<String, String> getOthers() {
    return other;
  }

  public void setOthers(final @Nullable Map<String, String> other) {
    this.other = other != null ? new ConcurrentHashMap<>(other) : null;
  }

  /**
   * the Request's unknown fields
   *
   * @return the unknown map
   */
  @TestOnly
  @Nullable
  Map<String, Object> getUnknown() {
    return unknown;
  }

  @ApiStatus.Internal
  @Override
  public void acceptUnknownProperties(final @NotNull Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  /**
   * Clones an User aka deep copy
   *
   * @return the cloned User
   * @throws CloneNotSupportedException if the User is not cloneable
   */
  @Override
  public @NotNull Request clone() throws CloneNotSupportedException {
    final Request clone = (Request) super.clone();

    clone.headers = CollectionUtils.shallowCopy(headers);
    clone.env = CollectionUtils.shallowCopy(env);
    clone.other = CollectionUtils.shallowCopy(other);
    clone.unknown = CollectionUtils.shallowCopy(unknown);

    return clone;
  }
}
