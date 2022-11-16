package io.sentry.util;

import io.sentry.SentryOptions;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class UrlUtils {

  private static final @NotNull Pattern USER_INFO_REGEX = Pattern.compile("(.+://)(.*@)(.*)");

  public static @Nullable String maybeStripSensitiveDataFromUrlNullable(
      final @Nullable String url, final @NotNull SentryOptions options) {
    if (url == null) {
      return null;
    }

    return maybeStripSensitiveDataFromUrl(url, options);
  }

  public static @NotNull String maybeStripSensitiveDataFromUrl(
      final @NotNull String url, final @NotNull SentryOptions options) {
    if (options.isSendDefaultPii()) {
      return url;
    }

    @NotNull String modifiedUrl = url;

    final @NotNull Matcher userInfoMatcher = USER_INFO_REGEX.matcher(modifiedUrl);
    if (userInfoMatcher.matches() && userInfoMatcher.groupCount() == 3) {
      final @NotNull String userInfoString = userInfoMatcher.group(2);
      final @NotNull String replacementString = userInfoString.contains(":") ? "%s:%s@" : "%s@";
      modifiedUrl = userInfoMatcher.group(1) + replacementString + userInfoMatcher.group(3);
    }

    final int queryParamSeparatorIndex = modifiedUrl.indexOf("?");
    if (queryParamSeparatorIndex >= 0) {
      final @NotNull String urlWithoutQuery =
          modifiedUrl.substring(0, queryParamSeparatorIndex).trim();
      final @NotNull String query = modifiedUrl.substring(queryParamSeparatorIndex).trim();

      modifiedUrl = urlWithoutQuery + maybeStripSensitiveDataFromQuery(query, options);
    }

    return modifiedUrl;
  }

  public static @NotNull String maybeStripSensitiveDataFromQuery(
      @NotNull String query, final @NotNull SentryOptions options) {
    if (options.isSendDefaultPii()) {
      return query;
    }

    final @NotNull StringBuilder queryBuilder = new StringBuilder();
    @NotNull String modifiedQuery = query;
    @NotNull String anchorPart = "";

    final int anchorInQueryIndex = modifiedQuery.indexOf("#");
    if (anchorInQueryIndex >= 0) {
      anchorPart = query.substring(anchorInQueryIndex);
      modifiedQuery = query.substring(0, anchorInQueryIndex);
    }

    final @NotNull String[] queryParams = modifiedQuery.split("&", -1);
    @NotNull String separator = "";
    for (final @NotNull String queryParam : queryParams) {
      final @NotNull String[] queryParamParts = queryParam.split("=", -1);
      queryBuilder.append(separator);
      if (queryParamParts.length == 2) {
        queryBuilder.append(queryParamParts[0]);
        queryBuilder.append("=%s");
      } else {
        queryBuilder.append(queryParam);
      }
      separator = "&";
    }

    queryBuilder.append(anchorPart);

    return queryBuilder.toString();
  }
}
