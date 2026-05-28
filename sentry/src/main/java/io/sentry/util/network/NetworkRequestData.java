package io.sentry.util.network;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Data class for tracking network request and response information in replays. Used by various HTTP
 * integrations (OkHttp, Apache HttpClient, etc.) to capture network data for replay functionality.
 * see
 * https://github.com/getsentry/sentry-javascript/blob/632f0b953d99050c11b0edafb9f80b5f3ba88045/packages/replay-internal/src/types/performance.ts#L133-L140
 */
public final class NetworkRequestData {
  private @Nullable final String method;
  private @Nullable Integer statusCode;
  private @Nullable Long requestBodySize;
  private @Nullable Long responseBodySize;
  private @Nullable ReplayNetworkRequestOrResponse request;
  private @Nullable ReplayNetworkRequestOrResponse response;

  public NetworkRequestData(@Nullable final String method) {
    this.method = method;
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

  /**
   * Populates this instance with request details obtained via {@link
   * NetworkDetailCaptureUtils#createRequest}
   */
  public void setRequestDetails(@NotNull final ReplayNetworkRequestOrResponse requestData) {
    this.request = requestData;
    this.requestBodySize = requestData.getSize();
  }

  /**
   * Populates this instance with request details obtained via {@link
   * NetworkDetailCaptureUtils#createResponse}
   */
  public void setResponseDetails(
      final int statusCode, @NotNull final ReplayNetworkRequestOrResponse responseData) {
    this.statusCode = statusCode;
    this.response = responseData;
    this.responseBodySize = responseData.getSize();
  }

  @Override
  public String toString() {
    return "NetworkRequestData{"
        + "method='"
        + method
        + '\''
        + ", statusCode="
        + statusCode
        + ", requestBodySize="
        + requestBodySize
        + ", responseBodySize="
        + responseBodySize
        + ", request="
        + request
        + ", response="
        + response
        + '}';
  }
}
