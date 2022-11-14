package io.sentry.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class UrlUtils {

  private static final @NotNull Pattern USER_INFO_REGEX = Pattern.compile("(.+://)(.*@)(.*)");
  private static final @NotNull Pattern TOKEN_REGEX = Pattern.compile("(.*)([?&]token=[^&#]+)(.*)");

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

    Matcher userInfoMatcher = USER_INFO_REGEX.matcher(modifiedUrl);
    if (userInfoMatcher.matches() && userInfoMatcher.groupCount() == 3) {
      final @NotNull String userInfoString = userInfoMatcher.group(2);
      final @NotNull String replacementString = userInfoString.contains(":") ? "%s:%s@" : "%s@";
      modifiedUrl = userInfoMatcher.group(1) + replacementString + userInfoMatcher.group(3);
    }

    Matcher tokenMatcher = TOKEN_REGEX.matcher(modifiedUrl);
    if (tokenMatcher.matches() && tokenMatcher.groupCount() == 3) {
      final @NotNull String tokenString = tokenMatcher.group(2);
      final @NotNull String queryParamSeparator = tokenString.substring(0, 1);
      modifiedUrl =
          tokenMatcher.group(1) + queryParamSeparator + "token=%s" + tokenMatcher.group(3);
    }

    return modifiedUrl;
  }
}
