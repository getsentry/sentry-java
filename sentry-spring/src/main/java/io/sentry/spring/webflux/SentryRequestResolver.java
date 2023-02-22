package io.sentry.spring.webflux;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.IHub;
import io.sentry.protocol.Request;
import io.sentry.util.HttpUtils;
import io.sentry.util.Objects;
import io.sentry.util.UrlUtils;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;

@Open
@ApiStatus.Experimental
public class SentryRequestResolver {
  private final @NotNull IHub hub;

  public SentryRequestResolver(final @NotNull IHub hub) {
    this.hub = Objects.requireNonNull(hub, "options is required");
  }

  public @NotNull Request resolveSentryRequest(final @NotNull ServerHttpRequest httpRequest) {
    final Request sentryRequest = new Request();
    final String methodName =
        httpRequest.getMethod() != null ? httpRequest.getMethod().name() : "unknown";
    sentryRequest.setMethod(methodName);
    final @NotNull URI uri = httpRequest.getURI();
    final @NotNull UrlUtils.UrlDetails urlDetails = UrlUtils.parse(uri.toString());
    urlDetails.applyToRequest(sentryRequest);
    sentryRequest.setHeaders(resolveHeadersMap(httpRequest.getHeaders()));

    if (hub.getOptions().isSendDefaultPii()) {
      sentryRequest.setCookies(toString(httpRequest.getHeaders().get("Cookies")));
    }
    return sentryRequest;
  }

  @NotNull
  Map<String, String> resolveHeadersMap(final HttpHeaders request) {
    final Map<String, String> headersMap = new HashMap<>();
    for (Map.Entry<String, List<String>> entry : request.entrySet()) {
      // do not copy personal information identifiable headers
      if (hub.getOptions().isSendDefaultPii()
          || !HttpUtils.containsSensitiveHeader(entry.getKey())) {
        headersMap.put(entry.getKey(), toString(entry.getValue()));
      }
    }
    return headersMap;
  }

  private static @Nullable String toString(final @Nullable List<String> enumeration) {
    return enumeration != null ? String.join(",", enumeration) : null;
  }
}
