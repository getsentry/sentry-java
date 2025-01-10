package io.sentry.servlet;

import io.sentry.EventProcessor;
import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.protocol.Request;
import io.sentry.util.HttpUtils;
import io.sentry.util.Objects;
import io.sentry.util.UrlUtils;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Attaches information about HTTP request to {@link SentryEvent}. */
final class SentryRequestHttpServletRequestProcessor implements EventProcessor {

  private final @NotNull HttpServletRequest httpRequest;

  public SentryRequestHttpServletRequestProcessor(@NotNull HttpServletRequest httpRequest) {
    this.httpRequest = Objects.requireNonNull(httpRequest, "httpRequest is required");
  }

  // httpRequest.getRequestURL() returns StringBuffer which is considered an obsolete class.
  @SuppressWarnings("JdkObsolete")
  @Override
  public @NotNull SentryEvent process(@NotNull SentryEvent event, @NotNull Hint hint) {
    final Request sentryRequest = new Request();
    sentryRequest.setMethod(httpRequest.getMethod());
    final @NotNull UrlUtils.UrlDetails urlDetails =
        UrlUtils.parse(httpRequest.getRequestURL().toString());
    urlDetails.applyToRequest(sentryRequest);
    sentryRequest.setQueryString(httpRequest.getQueryString());
    sentryRequest.setHeaders(resolveHeadersMap(httpRequest));

    event.setRequest(sentryRequest);
    return event;
  }

  private @NotNull Map<String, String> resolveHeadersMap(
      final @NotNull HttpServletRequest request) {
    final Map<String, String> headersMap = new HashMap<>();
    for (String headerName : Collections.list(request.getHeaderNames())) {
      // do not copy personal information identifiable headers
      if (!HttpUtils.containsSensitiveHeader(headerName.toUpperCase(Locale.ROOT))) {
        headersMap.put(headerName, toString(request.getHeaders(headerName)));
      }
    }
    return headersMap;
  }

  private static @Nullable String toString(final @Nullable Enumeration<String> enumeration) {
    return enumeration != null ? String.join(",", Collections.list(enumeration)) : null;
  }

  @Override
  public @Nullable Long getOrder() {
    return 4000L;
  }
}
