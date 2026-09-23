package io.sentry.util;

import io.sentry.FilterString;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class MetricsUtils {
  private MetricsUtils() {}

  /** Checks a final metric name against case-insensitive exact matches and full regex matches. */
  public static boolean isIgnored(
      final @Nullable List<FilterString> ignoredMetrics, final @Nullable String name) {
    if (name == null || ignoredMetrics == null || ignoredMetrics.isEmpty()) {
      return false;
    }
    for (final FilterString ignoredMetric : ignoredMetrics) {
      if (ignoredMetric.getFilterString().equalsIgnoreCase(name)) {
        return true;
      }
    }
    for (final FilterString ignoredMetric : ignoredMetrics) {
      if (ignoredMetric.matches(name)) {
        return true;
      }
    }
    return false;
  }
}
