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
  private @Nullable Long requestBodySize;
  private @Nullable ReplayNetworkRequestOrResponse request;

  // The response can be filled in after this instance was handed to the scope: an integration that
  // captures a streamed body only knows it once the stream has been consumed. Keeping the three
  // response values behind one volatile reference means a reader on the replay thread sees either
  // nothing or the complete set, never a mix of the two.
  private volatile @Nullable ResponseDetails responseDetails;

  public NetworkRequestData(@Nullable final String method) {
    this.method = method;
  }

  public @Nullable String getMethod() {
    return method;
  }

  public @Nullable Integer getStatusCode() {
    final ResponseDetails details = responseDetails;
    return details == null ? null : details.statusCode;
  }

  public @Nullable Long getRequestBodySize() {
    return requestBodySize;
  }

  public @Nullable Long getResponseBodySize() {
    final ResponseDetails details = responseDetails;
    return details == null ? null : details.bodySize;
  }

  public @Nullable ReplayNetworkRequestOrResponse getRequest() {
    return request;
  }

  public @Nullable ReplayNetworkRequestOrResponse getResponse() {
    final ResponseDetails details = responseDetails;
    return details == null ? null : details.response;
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
    this.responseDetails = new ResponseDetails(statusCode, responseData.getSize(), responseData);
  }

  @Override
  public String toString() {
    return "NetworkRequestData{"
        + "method='"
        + method
        + '\''
        + ", statusCode="
        + getStatusCode()
        + ", requestBodySize="
        + requestBodySize
        + ", responseBodySize="
        + getResponseBodySize()
        + ", request="
        + request
        + ", response="
        + getResponse()
        + '}';
  }

  /** Immutable, so publishing one reference publishes all three values. */
  private static final class ResponseDetails {
    private final int statusCode;
    private final @Nullable Long bodySize;
    private final @NotNull ReplayNetworkRequestOrResponse response;

    ResponseDetails(
        final int statusCode,
        final @Nullable Long bodySize,
        final @NotNull ReplayNetworkRequestOrResponse response) {
      this.statusCode = statusCode;
      this.bodySize = bodySize;
      this.response = response;
    }
  }
}
