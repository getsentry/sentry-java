package io.sentry.spring;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.EventProcessor;
import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
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
@Open
public class SentryRequestHttpServletRequestProcessor implements EventProcessor {
  private static final List<String> SENSITIVE_HEADERS =
      Arrays.asList("X-FORWARDED-FOR", "AUTHORIZATION", "COOKIE");

  private final @NotNull HttpServletRequest request;
  private final @NotNull SentryOptions options;

  public SentryRequestHttpServletRequestProcessor(
      final @NotNull HttpServletRequest request, final @NotNull SentryOptions options) {
    this.request = Objects.requireNonNull(request, "request is required");
    this.options = Objects.requireNonNull(options, "options are required");
  }

  @Override
  public @NotNull SentryEvent process(
      final @NotNull SentryEvent event, final @Nullable Object hint) {
    event.setRequest(resolveSentryRequest(request));
    return event;
  }

  // httpRequest.getRequestURL() returns StringBuffer which is considered an obsolete class.
  @SuppressWarnings("JdkObsolete")
  private @NotNull Request resolveSentryRequest(final @NotNull HttpServletRequest httpRequest) {
    final Request sentryRequest = new Request();
    sentryRequest.setMethod(httpRequest.getMethod());
    sentryRequest.setQueryString(httpRequest.getQueryString());
    sentryRequest.setUrl(httpRequest.getRequestURL().toString());
    sentryRequest.setHeaders(resolveHeadersMap(httpRequest));

    if (options.isSendDefaultPii()) {
      sentryRequest.setCookies(toString(httpRequest.getHeaders("Cookie")));
    }
    return sentryRequest;
  }

  private @NotNull Map<String, String> resolveHeadersMap(
      final @NotNull HttpServletRequest request) {
    final Map<String, String> headersMap = new HashMap<>();
    for (String headerName : Collections.list(request.getHeaderNames())) {
      // do not copy personal information identifiable headers
      if (options.isSendDefaultPii() || !SENSITIVE_HEADERS.contains(headerName.toUpperCase())) {
        headersMap.put(headerName, toString(request.getHeaders(headerName)));
      }
    }
    return headersMap;
  }

  private static @Nullable String toString(final @Nullable Enumeration<String> enumeration) {
    return enumeration != null ? String.join(",", Collections.list(enumeration)) : null;
  }
}
