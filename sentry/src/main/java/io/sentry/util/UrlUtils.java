package io.sentry.util;

import io.sentry.ISpan;
import io.sentry.SpanDataConvention;
import io.sentry.protocol.Request;
import java.net.URI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class UrlUtils {

  public static final @NotNull String SENSITIVE_DATA_SUBSTITUTE = "[Filtered]";

  public static @Nullable UrlDetails parseNullable(final @Nullable String url) {
    return url == null ? null : parse(url);
  }

  public static @NotNull UrlDetails parse(final @NotNull String url) {
    try {
      URI uri = new URI(url);
      if (uri.isAbsolute() && !isValidAbsoluteUrl(uri)) {
        return new UrlDetails(null, null, null);
      }

      final @NotNull String schemeAndSeparator =
          uri.getScheme() == null ? "" : (uri.getScheme() + "://");
      final @NotNull String authority = uri.getRawAuthority() == null ? "" : uri.getRawAuthority();
      final @NotNull String path = uri.getRawPath() == null ? "" : uri.getRawPath();
      final @Nullable String query = uri.getRawQuery();
      final @Nullable String fragment = uri.getRawFragment();

      final @NotNull String filteredUrl = schemeAndSeparator + filterUserInfo(authority) + path;

      return new UrlDetails(filteredUrl, query, fragment);
    } catch (Exception e) {
      return new UrlDetails(null, null, null);
    }
  }

  private static boolean isValidAbsoluteUrl(final @NotNull URI uri) {
    try {
      uri.toURL();
    } catch (Exception e) {
      return false;
    }
    return true;
  }

  private static @NotNull String filterUserInfo(final @NotNull String url) {
    if (!url.contains("@")) {
      return url;
    }
    if (url.startsWith("@")) {
      return SENSITIVE_DATA_SUBSTITUTE + url;
    }
    final @NotNull String userInfo = url.substring(0, url.indexOf('@') - 1);
    final @NotNull String filteredUserInfo =
        userInfo.contains(":")
            ? (SENSITIVE_DATA_SUBSTITUTE + ":" + SENSITIVE_DATA_SUBSTITUTE)
            : SENSITIVE_DATA_SUBSTITUTE;
    return filteredUserInfo + url.substring(url.indexOf('@'));
  }

  public static final class UrlDetails {
    private final @Nullable String url;
    private final @Nullable String query;
    private final @Nullable String fragment;

    public UrlDetails(
        final @Nullable String url, final @Nullable String query, final @Nullable String fragment) {
      this.url = url;
      this.query = query;
      this.fragment = fragment;
    }

    public @Nullable String getUrl() {
      return url;
    }

    public @NotNull String getUrlOrFallback() {
      if (url == null) {
        return "unknown";
      } else {
        return url;
      }
    }

    public @Nullable String getQuery() {
      return query;
    }

    public @Nullable String getFragment() {
      return fragment;
    }

    public void applyToRequest(final @Nullable Request request) {
      if (request == null) {
        return;
      }

      request.setUrl(url);
      request.setQueryString(query);
      request.setFragment(fragment);
    }

    public void applyToSpan(final @Nullable ISpan span) {
      if (span == null) {
        return;
      }

      if (query != null) {
        span.setData(SpanDataConvention.HTTP_QUERY_KEY, query);
      }
      if (fragment != null) {
        span.setData(SpanDataConvention.HTTP_FRAGMENT_KEY, fragment);
      }
    }
  }
}
