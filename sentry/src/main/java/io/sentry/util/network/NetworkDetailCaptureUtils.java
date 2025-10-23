package io.sentry.util.network;

import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility class for network capture operations shared across HTTP client integrations. Provides
 * common logic for determining what network data to capture and filtering headers.
 */
public final class NetworkDetailCaptureUtils {

  private NetworkDetailCaptureUtils() {}

  /** Functional interface for extracting network body content from HTTP objects. */
  public interface NetworkBodyExtractor<T> {
    @Nullable
    NetworkBody extract(@NotNull T httpObject);
  }

  /** Functional interface for extracting headers from HTTP objects. */
  public interface NetworkHeaderExtractor<T> {
    @NotNull
    Map<String, String> extract(@NotNull T httpObject);
  }

  public static @Nullable NetworkRequestData initializeForUrl(
      @NotNull String url,
      @Nullable String method,
      @Nullable String[] networkDetailAllowUrls,
      @Nullable String[] networkDetailDenyUrls) {

    if (!shouldCaptureUrl(url, networkDetailAllowUrls, networkDetailDenyUrls)) {
      return null;
    }

    return new NetworkRequestData(method, null, null, null, null, null);
  }

  /**
   * Creates a ReplayNetworkRequestOrResponse for a request, extracting body and headers based on
   * configuration.
   */
  public static <T> @NotNull ReplayNetworkRequestOrResponse createRequest(
      @NotNull T httpObject,
      @Nullable Long bodySize,
      boolean networkCaptureBodies,
      @NotNull NetworkBodyExtractor<T> bodyExtractor,
      @NotNull String[] networkRequestHeaders,
      @NotNull NetworkHeaderExtractor<T> headerExtractor) {

    return createRequestOrResponseInternal(
        httpObject,
        bodySize,
        networkCaptureBodies,
        bodyExtractor,
        networkRequestHeaders,
        headerExtractor);
  }

  public static <T> @NotNull ReplayNetworkRequestOrResponse createResponse(
      @NotNull T httpObject,
      @Nullable Long bodySize,
      boolean networkCaptureBodies,
      @NotNull NetworkBodyExtractor<T> bodyExtractor,
      @NotNull String[] networkResponseHeaders,
      @NotNull NetworkHeaderExtractor<T> headerExtractor) {

    return createRequestOrResponseInternal(
        httpObject,
        bodySize,
        networkCaptureBodies,
        bodyExtractor,
        networkResponseHeaders,
        headerExtractor);
  }

  /**
   * Determines if detailed network data should be captured for the given URL. See
   * <a href="https://docs.sentry.io/platforms/javascript/session-replay/configuration/">docs.sentry.io</a>
   *
   * @param url The URL to check
   * @param networkDetailAllowUrls Array of regex patterns that allow capture (null means allow all)
   * @param networkDetailDenyUrls Array of regex patterns that deny capture (null means deny none)
   * @return true if the URL should be captured, false otherwise
   */
  private static boolean shouldCaptureUrl(
      @NotNull String url,
      @Nullable String[] networkDetailAllowUrls,
      @Nullable String[] networkDetailDenyUrls) {

    // If there are deny patterns and URL matches any, don't capture.
    if (networkDetailDenyUrls != null) {
      for (String pattern : networkDetailDenyUrls) {
        if (pattern != null && url.matches(pattern)) {
          return false;
        }
      }
    }

    // Only capture developer-provided urls.
    if (networkDetailAllowUrls == null) {
      return false;
    }

    // If there are allow patterns, URL must match at least one
    for (String pattern : networkDetailAllowUrls) {
      if (pattern != null && url.matches(pattern)) {
        return true;
      }
    }

    return false;
  }

  private static @NotNull Map<String, String> getCaptureHeaders(
      @Nullable Map<String, String> allHeaders, @NotNull String[] allowedHeaders) {

    Map<String, String> capturedHeaders = new HashMap<>();

    if (allHeaders == null) {
      return capturedHeaders;
    }

    for (String header : allowedHeaders) {
      String value = allHeaders.get(header);
      if (value != null) {
        capturedHeaders.put(header, value);
      }
    }

    return capturedHeaders;
  }

  private static <T> @NotNull ReplayNetworkRequestOrResponse createRequestOrResponseInternal(
      @NotNull T httpObject,
      @Nullable Long bodySize,
      boolean networkCaptureBodies,
      @NotNull NetworkBodyExtractor<T> bodyExtractor,
      @NotNull String[] allowedHeaders,
      @NotNull NetworkHeaderExtractor<T> headerExtractor) {

    NetworkBody body = null;

    if (networkCaptureBodies) {
      body = bodyExtractor.extract(httpObject);
    }

    Map<String, String> headers =
        getCaptureHeaders(headerExtractor.extract(httpObject), allowedHeaders);

    return new ReplayNetworkRequestOrResponse(bodySize, body, headers);
  }
}
