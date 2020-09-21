package io.sentry.servlet;

import io.sentry.EventProcessor;
import io.sentry.SentryEvent;
import io.sentry.protocol.Request;
import io.sentry.util.Objects;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Attaches information about HTTP request to {@link SentryEvent}. */
final class SentryRequestHttpServletRequestProcessor implements EventProcessor {
  private static final List<String> SENSITIVE_HEADERS =
      Arrays.asList("X-FORWARDED-FOR", "AUTHORIZATION", "COOKIE");

  private final @NotNull HttpServletRequest httpRequest;

  public SentryRequestHttpServletRequestProcessor(@NotNull HttpServletRequest httpRequest) {
    this.httpRequest = Objects.requireNonNull(httpRequest, "httpRequest is required");
  }

  // httpRequest.getRequestURL() returns StringBuffer which is considered an obsolete class.
  @SuppressWarnings("JdkObsolete")
  @Override
  public @NotNull SentryEvent process(@NotNull SentryEvent event, @Nullable Object hint) {
    final Request sentryRequest = new Request();
    sentryRequest.setMethod(httpRequest.getMethod());
    sentryRequest.setQueryString(httpRequest.getQueryString());
    sentryRequest.setUrl(httpRequest.getRequestURL().toString());
    sentryRequest.setHeaders(resolveHeadersMap(httpRequest));

    event.setRequest(sentryRequest);
    return event;
  }

  private @NotNull Map<String, String> resolveHeadersMap(
      final @NotNull HttpServletRequest request) {
    final Map<String, String> headersMap = new HashMap<>();
    for (String headerName : Collections.list(request.getHeaderNames())) {
      // do not copy personal information identifiable headers
      if (!SENSITIVE_HEADERS.contains(headerName.toUpperCase())) {
        headersMap.put(headerName, toString(request.getHeaders(headerName)));
      }
    }
    return headersMap;
  }

  private static @Nullable String toString(final @Nullable Enumeration<String> enumeration) {
    return enumeration != null ? String.join(",", Collections.list(enumeration)) : null;
  }
}
