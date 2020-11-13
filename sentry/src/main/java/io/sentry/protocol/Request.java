package io.sentry.protocol;

import io.sentry.IUnknownPropertiesConsumer;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;

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
public final class Request implements IUnknownPropertiesConsumer {
  /**
   * The URL of the request if available.
   *
   * <p>The query string can be declared either as part of the `url`, or separately in
   * `query_string`.
   */
  private String url;
  /** HTTP request method. */
  private String method;
  /**
   * The query string component of the URL.
   *
   * <p>Can be given as unparsed string, dictionary, or list of tuples. (Currently only unparsed
   * string is possible)
   *
   * <p>If the query string is not declared and part of the `url`, Sentry moves it to the query
   * string.
   */
  private String queryString;
  /**
   * Request data in any format that makes sense.
   *
   * <p>SDKs should discard large and binary bodies by default. Can be given as string or structural
   * data of any format.
   */
  private Object data;
  /**
   * The cookie values.
   *
   * <p>Can be given unparsed as string, as dictionary, or as a list of tuples.
   */
  private String cookies;
  /**
   * A dictionary of submitted headers.
   *
   * <p>If a header appears multiple times it, needs to be merged according to the HTTP standard for
   * header merging. Header names are treated case-insensitively by Sentry.
   */
  private Map<String, String> headers;
  /**
   * Server environment data, such as CGI/WSGI.
   *
   * <p>A dictionary containing environment information passed from the server. This is where
   * information such as CGI/WSGI/Rack keys go that are not HTTP headers.
   *
   * <p>Sentry will explicitly look for `REMOTE_ADDR` to extract an IP address.
   */
  private Map<String, String> env;

  private Map<String, String> other;

  @SuppressWarnings("unused")
  private Map<String, Object> unknown;

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getMethod() {
    return method;
  }

  public void setMethod(String method) {
    this.method = method;
  }

  public String getQueryString() {
    return queryString;
  }

  public void setQueryString(String queryString) {
    this.queryString = queryString;
  }

  public Object getData() {
    return data;
  }

  public void setData(Object data) {
    this.data = data;
  }

  public String getCookies() {
    return cookies;
  }

  public void setCookies(String cookies) {
    this.cookies = cookies;
  }

  public Map<String, String> getHeaders() {
    return headers;
  }

  public void setHeaders(Map<String, String> headers) {
    this.headers = headers;
  }

  public Map<String, String> getEnvs() {
    return env;
  }

  public void setEnvs(Map<String, String> env) {
    this.env = env;
  }

  public Map<String, String> getOthers() {
    return other;
  }

  public void setOthers(Map<String, String> other) {
    this.other = other;
  }

  @ApiStatus.Internal
  @Override
  public void acceptUnknownProperties(Map<String, Object> unknown) {
    this.unknown = unknown;
  }
}
