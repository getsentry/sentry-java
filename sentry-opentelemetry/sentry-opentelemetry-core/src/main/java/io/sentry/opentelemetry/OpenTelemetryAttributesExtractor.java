package io.sentry.opentelemetry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import io.sentry.IScope;
import io.sentry.ISpan;
import io.sentry.protocol.Request;
import io.sentry.util.UrlUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class OpenTelemetryAttributesExtractor {

  public void extract(
      final @NotNull SpanData otelSpan,
      final @NotNull ISpan sentrySpan,
      final @NotNull IScope scope) {
    final @NotNull Attributes attributes = otelSpan.getAttributes();
    addRequestAttributesToScope(attributes, scope);
  }

  private void addRequestAttributesToScope(
      final @NotNull Attributes attributes, final @NotNull IScope scope) {
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
        final @Nullable String url = extractUrl(attributes);
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
    }
  }

  public @Nullable String extractUrl(final @NotNull Attributes attributes) {
    final @Nullable String urlFull = attributes.get(UrlAttributes.URL_FULL);
    if (urlFull != null) {
      return urlFull;
    }

    final String urlString = buildUrlString(attributes);
    if (!urlString.isEmpty()) {
      return urlString;
    }

    return null;
  }

  private @NotNull String buildUrlString(final @NotNull Attributes attributes) {
    final @Nullable String scheme = attributes.get(UrlAttributes.URL_SCHEME);
    final @Nullable String serverAddress = attributes.get(ServerAttributes.SERVER_ADDRESS);
    final @Nullable Long serverPort = attributes.get(ServerAttributes.SERVER_PORT);
    final @Nullable String path = attributes.get(UrlAttributes.URL_PATH);

    if (scheme == null || serverAddress == null) {
      return "";
    }

    final @NotNull StringBuilder urlBuilder = new StringBuilder();
    urlBuilder.append(scheme);
    urlBuilder.append("://");

    if (serverAddress != null) {
      urlBuilder.append(serverAddress);
      if (serverPort != null) {
        urlBuilder.append(":");
        urlBuilder.append(serverPort);
      }
    }

    if (path != null) {
      urlBuilder.append(path);
    }

    return urlBuilder.toString();
  }
}
