package io.sentry.util.network;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Data class for tracking network request and response information in replays. Used by various HTTP
 * integrations (OkHttp, Apache HttpClient, etc.) to capture network data for replay functionality.
 * see
 * https://github.com/getsentry/sentry-javascript/blob/632f0b953d99050c11b0edafb9f80b5f3ba88045/packages/replay-internal/src/types/performance.ts#L133-L140
 */
@ApiStatus.Internal
public final class NetworkRequestData {
  private @Nullable final String method;

  // volatile: both sides are filled in by the thread running the call and reading the body, and
  // read by the replay thread.
  private volatile @Nullable ReplayNetworkRequestOrResponse request;
  private volatile @Nullable ReplayNetworkRequestOrResponse response;

  public NetworkRequestData(@Nullable final String method) {
    this.method = method;
  }

  public @Nullable String getMethod() {
    return method;
  }

  public @Nullable Integer getStatusCode() {
    final ReplayNetworkRequestOrResponse responseData = response;
    return responseData == null ? null : responseData.getStatusCode();
  }

  public @Nullable Long getRequestBodySize() {
    final ReplayNetworkRequestOrResponse requestData = request;
    return requestData == null ? null : requestData.getSize();
  }

  public @Nullable Long getResponseBodySize() {
    final ReplayNetworkRequestOrResponse responseData = response;
    return responseData == null ? null : responseData.getSize();
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
  }

  /**
   * Populates this instance with response details obtained via {@link
   * NetworkDetailCaptureUtils#createResponse}.
   *
   * <p>May be called from another thread than the one that created this instance, and after the
   * instance was handed to the scope. A later call replaces the details of an earlier one, so an
   * integration can record the status code and the headers as soon as the response arrives and add
   * the body once it has been consumed.
   */
  public void setResponseDetails(@NotNull final ReplayNetworkRequestOrResponse responseData) {
    this.response = responseData;
  }

  @Override
  public String toString() {
    return "NetworkRequestData{"
        + "method='"
        + method
        + '\''
        + ", request="
        + request
        + ", response="
        + response
        + '}';
  }
}
