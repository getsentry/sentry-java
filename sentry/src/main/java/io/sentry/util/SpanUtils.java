package io.sentry.util;

import io.sentry.FilterString;
import io.sentry.SentryOpenTelemetryMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SpanUtils {

  /**
   * A list of span origins that are ignored by default when using OpenTelemetry.
   *
   * @return a list of span origins to be ignored
   */
  public static @NotNull List<String> ignoredSpanOriginsForOpenTelemetry(
      final @NotNull SentryOpenTelemetryMode mode) {
    final @NotNull List<String> origins = new ArrayList<>();

    if (SentryOpenTelemetryMode.AGENT == mode || SentryOpenTelemetryMode.AGENTLESS_SPRING == mode) {
      origins.add("auto.http.spring_jakarta.webmvc");
      origins.add("auto.http.spring.webmvc");
      origins.add("auto.http.spring7.webmvc");
      origins.add("auto.spring_jakarta.webflux");
      origins.add("auto.spring.webflux");
      origins.add("auto.spring7.webflux");
      origins.add("auto.db.jdbc");
      origins.add("auto.http.spring_jakarta.webclient");
      origins.add("auto.http.spring.webclient");
      origins.add("auto.http.spring7.webclient");
      origins.add("auto.http.spring_jakarta.restclient");
      origins.add("auto.http.spring.restclient");
      origins.add("auto.http.spring7.restclient");
      origins.add("auto.http.spring_jakarta.resttemplate");
      origins.add("auto.http.spring.resttemplate");
      origins.add("auto.http.spring7.resttemplate");
      origins.add("auto.http.openfeign");
      origins.add("auto.http.ktor-client");
    }

    if (SentryOpenTelemetryMode.AGENT == mode) {
      origins.add("auto.graphql.graphql");
      origins.add("auto.graphql.graphql22");
    }

    return origins;
  }

  private static final Map<String, Boolean> ignoredSpanDecisionsCache = new ConcurrentHashMap<>();

  /** Checks if a span origin has been ignored. */
  @ApiStatus.Internal
  public static boolean isIgnored(
      final @Nullable List<FilterString> ignoredOrigins, final @Nullable String origin) {
    if (origin == null || ignoredOrigins == null || ignoredOrigins.isEmpty()) {
      return false;
    }

    if (ignoredSpanDecisionsCache.containsKey(origin)) {
      return ignoredSpanDecisionsCache.get(origin);
    }

    for (final FilterString ignoredOrigin : ignoredOrigins) {
      if (ignoredOrigin.getFilterString().equalsIgnoreCase(origin)) {
        ignoredSpanDecisionsCache.put(origin, true);
        return true;
      }
    }

    for (final FilterString ignoredOrigin : ignoredOrigins) {
      try {
        if (ignoredOrigin.matches(origin)) {
          ignoredSpanDecisionsCache.put(origin, true);
          return true;
        }
      } catch (Throwable t) {
        // ignore invalid regex
      }
    }

    ignoredSpanDecisionsCache.put(origin, false);
    return false;
  }
}
