package io.sentry.spring.jakarta;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.IHub;
import io.sentry.protocol.Request;
import io.sentry.util.HttpUtils;
import io.sentry.util.Objects;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import io.sentry.util.UrlUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Open
public class SentryRequestResolver {
  private final @NotNull IHub hub;

  public SentryRequestResolver(final @NotNull IHub hub) {
    this.hub = Objects.requireNonNull(hub, "options is required");
  }

  // httpRequest.getRequestURL() returns StringBuffer which is considered an obsolete class.
  @SuppressWarnings("JdkObsolete")
  public @NotNull Request resolveSentryRequest(final @NotNull HttpServletRequest httpRequest) {
    final Request sentryRequest = new Request();
    sentryRequest.setMethod(httpRequest.getMethod());
    final @NotNull UrlUtils.UrlDetails urlDetails = UrlUtils.parse(httpRequest.getRequestURL().toString());
    urlDetails.applyToRequest(sentryRequest);
    sentryRequest.setQueryString(httpRequest.getQueryString());
    sentryRequest.setHeaders(resolveHeadersMap(httpRequest));

    if (hub.getOptions().isSendDefaultPii()) {
      sentryRequest.setCookies(toString(httpRequest.getHeaders("Cookie")));
    }
    return sentryRequest;
  }

  @NotNull
  Map<String, String> resolveHeadersMap(final @NotNull HttpServletRequest request) {
    final Map<String, String> headersMap = new HashMap<>();
    for (String headerName : Collections.list(request.getHeaderNames())) {
      // do not copy personal information identifiable headers
      if (hub.getOptions().isSendDefaultPii() || !HttpUtils.containsSensitiveHeader(headerName)) {
        headersMap.put(headerName, toString(request.getHeaders(headerName)));
      }
    }
    return headersMap;
  }

  private static @Nullable String toString(final @Nullable Enumeration<String> enumeration) {
    return enumeration != null ? String.join(",", Collections.list(enumeration)) : null;
  }
}
