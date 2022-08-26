package io.sentry.spring.webflux;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.IHub;
import io.sentry.protocol.Request;
import io.sentry.util.Objects;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;

@Open
@ApiStatus.Experimental
public class SentryRequestResolver {
  private static final List<String> SENSITIVE_HEADERS =
      Arrays.asList("X-FORWARDED-FOR", "AUTHORIZATION", "COOKIE");
  private final boolean isSendDefaultPii;

//  private final @NotNull IHub hub;

  public SentryRequestResolver(final boolean isSendDefaultPii) {
//    this.hub = Objects.requireNonNull(hub, "options is required");
    this.isSendDefaultPii = isSendDefaultPii;
  }

  public @NotNull Request resolveSentryRequest(final @NotNull ServerHttpRequest httpRequest) {
    final Request sentryRequest = new Request();
    sentryRequest.setMethod(httpRequest.getMethodValue());
    sentryRequest.setQueryString(httpRequest.getURI().getQuery());
    sentryRequest.setUrl(httpRequest.getURI().toString());
    sentryRequest.setHeaders(resolveHeadersMap(httpRequest.getHeaders()));

    if (isSendDefaultPii) {
      sentryRequest.setCookies(toString(httpRequest.getHeaders().get("Cookies")));
    }
    return sentryRequest;
  }

  @NotNull
  Map<String, String> resolveHeadersMap(final HttpHeaders request) {
    final Map<String, String> headersMap = new HashMap<>();
    for (Map.Entry<String, List<String>> entry : request.entrySet()) {
      // do not copy personal information identifiable headers
      if (isSendDefaultPii
          || !SENSITIVE_HEADERS.contains(entry.getKey().toUpperCase(Locale.ROOT))) {
        headersMap.put(entry.getKey(), toString(entry.getValue()));
      }
    }
    return headersMap;
  }

  private static @Nullable String toString(final @Nullable List<String> enumeration) {
    return enumeration != null ? String.join(",", enumeration) : null;
  }
}
