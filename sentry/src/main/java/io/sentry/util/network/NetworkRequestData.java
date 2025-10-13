package io.sentry.util.network;

import org.jetbrains.annotations.Nullable;

/**
 * Data class for tracking network request and response information in replays.
 * Used by various HTTP integrations (OkHttp, Apache HttpClient, etc.) to capture
 * network data for replay functionality.
 * see https://github.com/getsentry/sentry-javascript/blob/632f0b953d99050c11b0edafb9f80b5f3ba88045/packages/replay-internal/src/types/performance.ts#L133-L140
 */
public final class NetworkRequestData {
  private final @Nullable String method;
  private final @Nullable Integer statusCode;
  private final @Nullable Long requestBodySize;
  private final @Nullable Long responseBodySize;
  private final @Nullable ReplayNetworkRequestOrResponse request;
  private final @Nullable ReplayNetworkRequestOrResponse response;

  public NetworkRequestData(
      @Nullable String method,
      @Nullable Integer statusCode,
      @Nullable Long requestBodySize,
      @Nullable Long responseBodySize,
      @Nullable ReplayNetworkRequestOrResponse request,
      @Nullable ReplayNetworkRequestOrResponse response) {
    this.method = method;
    this.statusCode = statusCode;
    this.requestBodySize = requestBodySize;
    this.responseBodySize = responseBodySize;
    this.request = request;
    this.response = response;
  }

  public @Nullable String getMethod() {
    return method;
  }

  public @Nullable Integer getStatusCode() {
    return statusCode;
  }

  public @Nullable Long getRequestBodySize() {
    return requestBodySize;
  }

  public @Nullable Long getResponseBodySize() {
    return responseBodySize;
  }

  public @Nullable ReplayNetworkRequestOrResponse getRequest() {
    return request;
  }

  public @Nullable ReplayNetworkRequestOrResponse getResponse() {
    return response;
  }

  @Override
  public String toString() {
    return "NetworkRequestData{" +
        "method='" + method + '\'' +
        ", statusCode=" + statusCode +
        ", requestBodySize=" + requestBodySize +
        ", responseBodySize=" + responseBodySize +
        ", request=" + request +
        ", response=" + response +
        '}';
  }
}
