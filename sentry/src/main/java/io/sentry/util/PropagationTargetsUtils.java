package io.sentry.util;

import java.net.URI;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** Checks if an URL matches the list of origins. */
@ApiStatus.Internal
public final class PropagationTargetsUtils {

  public static boolean contain(final @NotNull List<String> origins, final @NotNull String url) {
    if (origins.isEmpty()) {
      return false;
    }
    for (final String origin : origins) {
      if (url.contains(origin)) {
        return true;
      }
      try {
        if (url.matches(origin)) {
          return true;
        }
      } catch (Exception e) {
        // ignore invalid regex
      }
    }
    return false;
  }

  public static boolean contain(final @NotNull List<String> origins, final URI uri) {
    return contain(origins, uri.toString());
  }
}
