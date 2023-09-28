package io.sentry.util;

import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Checks if a check-in for a monitor (CRON) has been ignored. */
@ApiStatus.Internal
public final class CheckInUtils {

  public static boolean isIgnored(
      final @Nullable List<String> ignoredSlugs, final @NotNull String slug) {
    if (ignoredSlugs == null || ignoredSlugs.isEmpty()) {
      return false;
    }

    for (final String ignoredSlug : ignoredSlugs) {
      if (ignoredSlug.equalsIgnoreCase(slug)) {
        return true;
      }

      try {
        if (slug.matches(ignoredSlug)) {
          return true;
        }
      } catch (Throwable t) {
        // ignore invalid regex
      }
    }

    return false;
  }
}
