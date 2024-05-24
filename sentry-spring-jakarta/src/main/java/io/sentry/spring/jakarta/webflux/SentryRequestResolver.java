package io.sentry.spring.jakarta.webflux;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.IScopes;
import io.sentry.protocol.Request;
import io.sentry.util.HttpUtils;
import io.sentry.util.Objects;
import io.sentry.util.UrlUtils;
import java.net.URI;
import java.util.Collections;
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
  private final @NotNull IScopes scopes;

  public SentryRequestResolver(final @NotNull IScopes scopes) {
    this.scopes = Objects.requireNonNull(scopes, "scopes are required");
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

    if (scopes.getOptions().isSendDefaultPii()) {
      String headerName = HttpUtils.COOKIE_HEADER_NAME;
      sentryRequest.setCookies(
          toString(
              HttpUtils.filterOutSecurityCookiesFromHeader(
                  httpRequest.getHeaders().get(headerName), headerName, Collections.emptyList())));
    }
    return sentryRequest;
  }

  @NotNull
  Map<String, String> resolveHeadersMap(final HttpHeaders request) {
    final Map<String, String> headersMap = new HashMap<>();
    for (Map.Entry<String, List<String>> entry : request.entrySet()) {
      // do not copy personal information identifiable headers
      String headerName = entry.getKey();
      if (scopes.getOptions().isSendDefaultPii()
          || !HttpUtils.containsSensitiveHeader(headerName)) {
        headersMap.put(
            headerName,
            toString(
                HttpUtils.filterOutSecurityCookiesFromHeader(
                    entry.getValue(), headerName, Collections.emptyList())));
      }
    }
    return headersMap;
  }

  private static @Nullable String toString(final @Nullable List<String> enumeration) {
    return enumeration != null ? String.join(",", enumeration) : null;
  }
}
