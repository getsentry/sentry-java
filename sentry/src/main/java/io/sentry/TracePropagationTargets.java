package io.sentry;

import java.net.URI;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Checks if an URL matches the list of origins to which `sentry-trace` header should be sent in
 * HTTP integrations.
 */
public final class TracePropagationTargets {

  public static boolean contain(final @NotNull List<String> origins, final @NotNull String url) {
    if (origins.isEmpty()) {
      return false;
    }
    for (final String origin : origins) {
      if (url.contains(origin) || url.matches(origin)) {
        return true;
      }
    }
    return false;
  }

  public static boolean contain(final @NotNull List<String> origins, final URI uri) {
    return contain(origins, uri.toString());
  }
}
