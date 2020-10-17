package io.sentry.spring.reactive;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.EventProcessor;
import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import io.sentry.protocol.Request;
import io.sentry.util.Objects;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;

/** Attaches information about Reactive HTTP request to {@link SentryEvent}. */
@Open
public class SentryReactiveWebRequestProcessor implements EventProcessor {
  private static final List<String> SENSITIVE_HEADERS =
      Arrays.asList("X-FORWARDED-FOR", "AUTHORIZATION", "COOKIE");

  private final @NotNull ServerHttpRequest request;
  private final @NotNull SentryOptions options;

  public SentryReactiveWebRequestProcessor(
      final @NotNull ServerHttpRequest request, final @NotNull SentryOptions options) {
    this.request = Objects.requireNonNull(request, "request is required");
    this.options = Objects.requireNonNull(options, "options are required");
  }

  @Override
  public @NotNull SentryEvent process(
      final @NotNull SentryEvent event, final @Nullable Object hint) {
    event.setRequest(resolveSentryRequest(request));
    return event;
  }

  private @NotNull Request resolveSentryRequest(final @NotNull ServerHttpRequest httpRequest) {
    final Request sentryRequest = new Request();
    sentryRequest.setMethod(httpRequest.getMethodValue());
    sentryRequest.setQueryString(httpRequest.getURI().getQuery());
    sentryRequest.setUrl(httpRequest.getURI().toString().replaceFirst("\\?.*$", ""));
    sentryRequest.setHeaders(resolveHeadersMap(httpRequest));
    if (options.isSendDefaultPii()) {
      sentryRequest.setCookies(
          toString(httpRequest.getHeaders().getValuesAsList(HttpHeaders.COOKIE)));
    }
    return sentryRequest;
  }

  private @NotNull Map<String, String> resolveHeadersMap(
      final @NotNull ServerHttpRequest httpRequest) {
    final Map<String, String> headersMap = new HashMap<>();
    for (String headerName : httpRequest.getHeaders().keySet()) {
      // do not copy personal information identifiable headers
      if (options.isSendDefaultPii() || !SENSITIVE_HEADERS.contains(headerName.toUpperCase())) {
        headersMap.put(headerName, toString(httpRequest.getHeaders().getValuesAsList(headerName)));
      }
    }
    return headersMap;
  }

  private static @Nullable String toString(final @Nullable List<String> list) {
    return list != null ? String.join(",", list) : null;
  }
}
