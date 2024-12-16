package io.sentry.util;

import java.util.ArrayList;
import java.util.List;

import io.sentry.FilterString;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SpanUtils {

  /**
   * A list of span origins that are ignored by default when using OpenTelemetry.
   *
   * @return a list of span origins to be ignored
   */
  public static @NotNull List<String> ignoredSpanOriginsForOpenTelemetry(final boolean isAgent) {
    final @NotNull List<String> origins = new ArrayList<>();

    origins.add("auto.http.spring_jakarta.webmvc");
    origins.add("auto.http.spring.webmvc");
    origins.add("auto.spring_jakarta.webflux");
    origins.add("auto.spring.webflux");
    origins.add("auto.db.jdbc");
    origins.add("auto.http.spring_jakarta.webclient");
    origins.add("auto.http.spring.webclient");
    origins.add("auto.http.spring_jakarta.restclient");
    origins.add("auto.http.spring.restclient");
    origins.add("auto.http.spring_jakarta.resttemplate");
    origins.add("auto.http.spring.resttemplate");
    origins.add("auto.http.openfeign");

    if (isAgent) {
      origins.add("auto.graphql.graphql");
      origins.add("auto.graphql.graphql22");
    }

    return origins;
  }

  /** Checks if a span origin has been ignored. */
  @ApiStatus.Internal
  public static boolean isIgnored(
      final @Nullable List<FilterString> ignoredOrigins, final @Nullable String origin) {
    if (origin == null || ignoredOrigins == null || ignoredOrigins.isEmpty()) {
      return false;
    }

    for (final FilterString ignoredOrigin : ignoredOrigins) {
      if (ignoredOrigin.getFilterString().equalsIgnoreCase(origin)) {
        return true;
      }
    }

    for (final FilterString ignoredOrigin : ignoredOrigins) {
      try {
        if (ignoredOrigin.matches(origin)) {
          return true;
        }
      } catch (Throwable t) {
        // ignore invalid regex
      }
    }

    return false;
  }
}
