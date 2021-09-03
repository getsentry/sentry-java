package io.sentry;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Contains a list of origins to which `sentry-trace` header should be sent in HTTP integrations.
 */
public final class TracingOrigins {
  private final @NotNull List<String> origins;

  public TracingOrigins(final @NotNull List<String> origins) {
    this.origins = origins;
  }

  public TracingOrigins(final @NotNull String... origins) {
    this(Arrays.asList(origins));
  }

  public boolean contain(final @NotNull String url) {
    if (origins.isEmpty()) {
      return true;
    }
    for (final String origin : origins) {
      if (url.contains(origin) || url.matches(origin)) {
        return true;
      }
    }
    return false;
  }

  public boolean contain(final URI uri) {
    return contain(uri.toString());
  }
}
