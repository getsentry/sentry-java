package io.sentry.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class UrlUtils {

  private static final @NotNull Pattern USER_INFO_REGEX = Pattern.compile("(.+://)(.*@)(.*)");

  public static @Nullable String maybeStripSensitiveDataFromUrlNullable(
      final @Nullable String url, final boolean isSendDefaultPii) {
    if (url == null) {
      return null;
    }

    return maybeStripSensitiveDataFromUrl(url, isSendDefaultPii);
  }

  public static @NotNull String maybeStripSensitiveDataFromUrl(
      final @NotNull String url, final boolean isSendDefaultPii) {
    if (isSendDefaultPii) {
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
      final @NotNull StringBuilder urlBuilder = new StringBuilder(urlWithoutQuery);
      @NotNull String query = modifiedUrl.substring(queryParamSeparatorIndex).trim();
      @NotNull String anchorPart = "";

      final int anchorInQueryIndex = query.indexOf("#");
      if (anchorInQueryIndex >= 0) {
        anchorPart = query.substring(anchorInQueryIndex);
        query = query.substring(0, anchorInQueryIndex);
      }

      final @NotNull String[] queryParams = query.split("&", -1);
      @NotNull String separator = "";
      for (final @NotNull String queryParam : queryParams) {
        final @NotNull String[] queryParamParts = queryParam.split("=", -1);
        urlBuilder.append(separator);
        if (queryParamParts.length == 2) {
          urlBuilder.append(queryParamParts[0]);
          urlBuilder.append("=%s");
        } else {
          urlBuilder.append(queryParam);
        }
        separator = "&";
      }

      urlBuilder.append(anchorPart);

      modifiedUrl = urlBuilder.toString();
    }

    return modifiedUrl;
  }
}
