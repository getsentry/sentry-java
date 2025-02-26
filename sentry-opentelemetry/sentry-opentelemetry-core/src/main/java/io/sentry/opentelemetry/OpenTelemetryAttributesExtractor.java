package io.sentry.opentelemetry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import io.sentry.IScope;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.protocol.Request;
import io.sentry.util.HttpUtils;
import io.sentry.util.StringUtils;
import io.sentry.util.UrlUtils;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class OpenTelemetryAttributesExtractor {

  private static final String HTTP_REQUEST_HEADER_PREFIX = "http.request.header.";

  public void extract(
      final @NotNull SpanData otelSpan,
      final @NotNull IScope scope,
      final @NotNull SentryOptions options) {
    final @NotNull Attributes attributes = otelSpan.getAttributes();
    if (attributes.get(HttpAttributes.HTTP_REQUEST_METHOD) != null) {
      addRequestAttributesToScope(attributes, scope, options);
    }
  }

  private void addRequestAttributesToScope(
      final @NotNull Attributes attributes,
      final @NotNull IScope scope,
      final @NotNull SentryOptions options) {
    if (scope.getRequest() == null) {
      scope.setRequest(new Request());
    }
    final @Nullable Request request = scope.getRequest();
    if (request != null) {
      final @Nullable String requestMethod = attributes.get(HttpAttributes.HTTP_REQUEST_METHOD);
      if (requestMethod != null) {
        request.setMethod(requestMethod);
      }

      if (request.getUrl() == null) {
        final @Nullable String url = extractUrl(attributes, options);
        if (url != null) {
          final @NotNull UrlUtils.UrlDetails urlDetails = UrlUtils.parse(url);
          urlDetails.applyToRequest(request);
        }
      }

      if (request.getQueryString() == null) {
        final @Nullable String query = attributes.get(UrlAttributes.URL_QUERY);
        if (query != null) {
          request.setQueryString(query);
        }
      }

      if (request.getHeaders() == null) {
        Map<String, String> headers = collectHeaders(attributes, options);
        if (!headers.isEmpty()) {
          request.setHeaders(headers);
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, String> collectHeaders(
      final @NotNull Attributes attributes, final @NotNull SentryOptions options) {
    Map<String, String> headers = new HashMap<>();

    attributes.forEach(
        (key, value) -> {
          final @NotNull String attributeKeyAsString = key.getKey();
          if (attributeKeyAsString.startsWith(HTTP_REQUEST_HEADER_PREFIX)) {
            final @NotNull String headerName =
                StringUtils.removePrefix(attributeKeyAsString, HTTP_REQUEST_HEADER_PREFIX);
            if (options.isSendDefaultPii() || !HttpUtils.containsSensitiveHeader(headerName)) {
              if (value instanceof List) {
                try {
                  final @NotNull List<String> headerValues = (List<String>) value;
                  headers.put(
                      headerName,
                      toString(
                          HttpUtils.filterOutSecurityCookiesFromHeader(
                              headerValues, headerName, null)));
                } catch (Throwable t) {
                  options
                      .getLogger()
                      .log(SentryLevel.WARNING, "Expected a List<String> as header", t);
                }
              }
            }
          }
        });
    return headers;
  }

  public @Nullable String extractUrl(
      final @NotNull Attributes attributes, final @NotNull SentryOptions options) {
    final @Nullable String urlFull = attributes.get(UrlAttributes.URL_FULL);
    if (urlFull != null) {
      return urlFull;
    }

    final String urlString = buildUrlString(attributes, options);
    if (!urlString.isEmpty()) {
      return urlString;
    }

    return null;
  }

  private static @Nullable String toString(final @Nullable List<String> list) {
    return list != null ? String.join(",", list) : null;
  }

  private @NotNull String buildUrlString(
      final @NotNull Attributes attributes, final @NotNull SentryOptions options) {
    final @Nullable String scheme = attributes.get(UrlAttributes.URL_SCHEME);
    final @Nullable String serverAddress = attributes.get(ServerAttributes.SERVER_ADDRESS);
    final @Nullable Long serverPort = attributes.get(ServerAttributes.SERVER_PORT);
    final @Nullable String path = attributes.get(UrlAttributes.URL_PATH);

    if (scheme == null || serverAddress == null) {
      return "";
    }

    try {
      final @NotNull String pathToUse = path == null ? "" : path;
      if (serverPort == null) {
        return new URL(scheme, serverAddress, pathToUse).toString();
      } else {
        return new URL(scheme, serverAddress, serverPort.intValue(), pathToUse).toString();
      }
    } catch (Throwable t) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Unable to combine URL span attributes into one.", t);
      return "";
    }
  }
}
