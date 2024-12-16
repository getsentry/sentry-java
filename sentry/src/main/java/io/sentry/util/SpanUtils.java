package io.sentry.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SpanUtils {

  public static class FilterString {
    private final String filterString;
    private final Pattern pattern;

    public FilterString(String filterString) {
      this.filterString = filterString;
      this.pattern = Pattern.compile(filterString);
    }

    public String getFilterString() {
      return filterString;
    }

    public boolean matches(String input) {
      return pattern.matcher(input).matches();
    }
  }

  /**
   * A list of span origins that are ignored by default when using OpenTelemetry.
   *
   * @return a list of span origins to be ignored
   */
  public static @NotNull List<FilterString> ignoredSpanOriginsForOpenTelemetry(final boolean isAgent) {
    final @NotNull List<FilterString> origins = new ArrayList<>();

    origins.add(new FilterString("auto.http.spring_jakarta.webmvc"));
    origins.add(new FilterString("auto.http.spring.webmvc"));
    origins.add(new FilterString("auto.spring_jakarta.webflux"));
    origins.add(new FilterString("auto.spring.webflux"));
    origins.add(new FilterString("auto.db.jdbc"));
    origins.add(new FilterString("auto.http.spring_jakarta.webclient"));
    origins.add(new FilterString("auto.http.spring.webclient"));
    origins.add(new FilterString("auto.http.spring_jakarta.restclient"));
    origins.add(new FilterString("auto.http.spring.restclient"));
    origins.add(new FilterString("auto.http.spring_jakarta.resttemplate"));
    origins.add(new FilterString("auto.http.spring.resttemplate"));
    origins.add(new FilterString("auto.http.openfeign"));

    if (isAgent) {
      origins.add(new FilterString("auto.graphql.graphql"));
      origins.add(new FilterString("auto.graphql.graphql22"));
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
