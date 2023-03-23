package io.sentry.util;

import io.sentry.ISpan;
import io.sentry.protocol.Request;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class UrlUtils {

  public static final @NotNull String SENSITIVE_DATA_SUBSTITUTE = "[Filtered]";
  private static final @NotNull Pattern AUTH_REGEX = Pattern.compile("(.+://)(.*@)(.*)");

  public static @Nullable UrlDetails parseNullable(final @Nullable String url) {
    if (url == null) {
      return null;
    }

    return parse(url);
  }

  public static @NotNull UrlDetails parse(final @NotNull String url) {
    if (isAbsoluteUrl(url)) {
      return splitAbsoluteUrl(url);
    } else {
      return splitRelativeUrl(url);
    }
  }

  private static boolean isAbsoluteUrl(@NotNull String url) {
    return url.contains("://");
  }

  private static @NotNull UrlDetails splitRelativeUrl(final @NotNull String url) {
    final int queryParamSeparatorIndex = url.indexOf("?");
    final int fragmentSeparatorIndex = url.indexOf("#");

    final @Nullable String baseUrl =
        extractBaseUrl(url, queryParamSeparatorIndex, fragmentSeparatorIndex);
    final @Nullable String query =
        extractQuery(url, queryParamSeparatorIndex, fragmentSeparatorIndex);
    final @Nullable String fragment = extractFragment(url, fragmentSeparatorIndex);

    return new UrlDetails(baseUrl, query, fragment);
  }

  private static @Nullable String extractBaseUrl(
      final @NotNull String url,
      final int queryParamSeparatorIndex,
      final int fragmentSeparatorIndex) {
    if (queryParamSeparatorIndex >= 0) {
      return url.substring(0, queryParamSeparatorIndex).trim();
    } else if (fragmentSeparatorIndex >= 0) {
      return url.substring(0, fragmentSeparatorIndex).trim();
    } else {
      return url;
    }
  }

  private static @Nullable String extractQuery(
      final @NotNull String url,
      final int queryParamSeparatorIndex,
      final int fragmentSeparatorIndex) {
    if (queryParamSeparatorIndex > 0) {
      if (fragmentSeparatorIndex > 0 && fragmentSeparatorIndex > queryParamSeparatorIndex) {
        return url.substring(queryParamSeparatorIndex + 1, fragmentSeparatorIndex).trim();
      } else {
        return url.substring(queryParamSeparatorIndex + 1).trim();
      }
    } else {
      return null;
    }
  }

  private static @Nullable String extractFragment(
      final @NotNull String url, final int fragmentSeparatorIndex) {
    if (fragmentSeparatorIndex > 0) {
      return url.substring(fragmentSeparatorIndex + 1).trim();
    } else {
      return null;
    }
  }

  private static @NotNull UrlDetails splitAbsoluteUrl(final @NotNull String url) {
    try {
      final @NotNull String filteredUrl = urlWithAuthRemoved(url);
      final @NotNull URL urlObj = new URL(url);
      final @NotNull String baseUrl = baseUrlOnly(filteredUrl);
      if (baseUrl.contains("#")) {
        // url considered malformed because it has fragment
        return new UrlDetails(null, null, null);
      } else {
        final @Nullable String query = urlObj.getQuery();
        final @Nullable String fragment = urlObj.getRef();
        return new UrlDetails(baseUrl, query, fragment);
      }
    } catch (MalformedURLException e) {
      return new UrlDetails(null, null, null);
    }
  }

  private static @NotNull String urlWithAuthRemoved(final @NotNull String url) {
    final @NotNull Matcher userInfoMatcher = AUTH_REGEX.matcher(url);
    if (userInfoMatcher.matches() && userInfoMatcher.groupCount() == 3) {
      final @NotNull String userInfoString = userInfoMatcher.group(2);
      final @NotNull String replacementString =
          userInfoString.contains(":")
              ? (SENSITIVE_DATA_SUBSTITUTE + ":" + SENSITIVE_DATA_SUBSTITUTE + "@")
              : (SENSITIVE_DATA_SUBSTITUTE + "@");
      return userInfoMatcher.group(1) + replacementString + userInfoMatcher.group(3);
    } else {
      return url;
    }
  }

  private static @NotNull String baseUrlOnly(final @NotNull String url) {
    final int queryParamSeparatorIndex = url.indexOf("?");

    if (queryParamSeparatorIndex >= 0) {
      return url.substring(0, queryParamSeparatorIndex).trim();
    } else {
      final int fragmentSeparatorIndex = url.indexOf("#");
      if (fragmentSeparatorIndex >= 0) {
        return url.substring(0, fragmentSeparatorIndex).trim();
      } else {
        return url;
      }
    }
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
        span.setData("http.query", query);
      }
      if (fragment != null) {
        span.setData("http.fragment", fragment);
      }
    }
  }
}
