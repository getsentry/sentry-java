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

  // Both sides are filled in by the thread running the http call and read by the replay thread, so
  // both are published through a volatile write. The response side needs it most: an integration
  // that captures a body of unknown length only knows the response once the body has been consumed,
  // which can be after this instance was handed to the scope. Keeping its values behind one
  // reference to an immutable object means a reader sees either nothing or the complete set.
  private volatile @Nullable ReplayNetworkRequestOrResponse request;
  private volatile @Nullable ResponseDetails responseDetails;

  public NetworkRequestData(@Nullable final String method) {
    this.method = method;
  }

  public @Nullable String getMethod() {
    return method;
  }

  public @Nullable Integer getStatusCode() {
    final ResponseDetails details = responseDetails;
    return details == null ? null : details.getStatusCode();
  }

  public @Nullable Long getRequestBodySize() {
    final ReplayNetworkRequestOrResponse requestData = request;
    return requestData == null ? null : requestData.getSize();
  }

  public @Nullable Long getResponseBodySize() {
    final ResponseDetails details = responseDetails;
    return details == null ? null : details.getResponse().getSize();
  }

  public @Nullable ReplayNetworkRequestOrResponse getRequest() {
    return request;
  }

  public @Nullable ReplayNetworkRequestOrResponse getResponse() {
    final ResponseDetails details = responseDetails;
    return details == null ? null : details.getResponse();
  }

  /**
   * Populates this instance with request details obtained via {@link
   * NetworkDetailCaptureUtils#createRequest}
   */
  public void setRequestDetails(@NotNull final ReplayNetworkRequestOrResponse requestData) {
    this.request = requestData;
  }

  /**
   * The response details as one snapshot, or {@code null} while the response is not known yet.
   *
   * <p>Prefer this over {@link #getStatusCode()}, {@link #getResponseBodySize()} and {@link
   * #getResponse()} when more than one of them is needed: the details may be replaced between two
   * of those calls, which would mix one response with the next.
   */
  public @Nullable ResponseDetails getResponseDetails() {
    return responseDetails;
  }

  /**
   * Populates this instance with the response details assembled by the caller.
   *
   * <p>May be called from another thread than the one that created this instance, and after the
   * instance was handed to the scope. A later call replaces the details of an earlier one, so an
   * integration can record the status code and the headers as soon as the response arrives and add
   * the body once it has been consumed.
   */
  public void setResponseDetails(@NotNull final ResponseDetails details) {
    this.responseDetails = details;
  }

  @Override
  public String toString() {
    return "NetworkRequestData{"
        + "method='"
        + method
        + '\''
        + ", request="
        + request
        + ", responseDetails="
        + responseDetails
        + '}';
  }

  /**
   * The response side of a {@link NetworkRequestData}, immutable so one reference publishes all.
   */
  public static final class ResponseDetails {
    private final int statusCode;
    private final @NotNull ReplayNetworkRequestOrResponse response;

    /**
     * @param statusCode the HTTP status code of the response.
     * @param response the response details obtained via {@link
     *     NetworkDetailCaptureUtils#createResponse}
     */
    public ResponseDetails(
        final int statusCode, final @NotNull ReplayNetworkRequestOrResponse response) {
      this.statusCode = statusCode;
      this.response = response;
    }

    public int getStatusCode() {
      return statusCode;
    }

    public @NotNull ReplayNetworkRequestOrResponse getResponse() {
      return response;
    }

    @Override
    public String toString() {
      return "ResponseDetails{statusCode=" + statusCode + ", response=" + response + '}';
    }
  }
}
