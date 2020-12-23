package io.sentry;

import io.sentry.util.Objects;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Represents common HTTP request properties that must be set on the requests sending {@link
 * SentryEnvelope}.
 */
public final class RequestDetails {
  private final @NotNull URL url;
  private final @NotNull Map<String, String> headers;

  public RequestDetails(final @NotNull String url, final @NotNull Map<String, String> headers) {
    Objects.requireNonNull(url, "url is required");
    Objects.requireNonNull(headers, "headers is required");
    try {
      this.url = URI.create(url).toURL();
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException("Failed to compose the Sentry's server URL.", e);
    }
    this.headers = headers;
  }

  public @NotNull URL getUrl() {
    return url;
  }

  public @NotNull Map<String, String> getHeaders() {
    return headers;
  }
}
