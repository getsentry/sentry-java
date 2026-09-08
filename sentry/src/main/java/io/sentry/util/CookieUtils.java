package io.sentry.util;

import static io.sentry.util.UrlUtils.SENSITIVE_DATA_SUBSTITUTE;

import io.sentry.KeyValueCollectionBehavior;
import io.sentry.SentryOptions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class CookieUtils {

  public static final String COOKIE_HEADER_NAME = "Cookie";

  private static final List<String> SECURITY_COOKIES =
      Arrays.asList(
          "JSESSIONID",
          "JSESSIONIDSSO",
          "JSSOSESSIONID",
          "SESSIONID",
          "SID",
          "CSRFTOKEN",
          "XSRF-TOKEN");

  public static @Nullable List<String> filterCookiesFromHeader(
      final @Nullable Enumeration<String> headers,
      final @NotNull KeyValueCollectionBehavior behavior,
      final @Nullable List<String> additionalSensitiveCookieNames) {
    return headers == null
        ? null
        : filterCookiesFromHeader(
            Collections.list(headers), behavior, additionalSensitiveCookieNames);
  }

  public static @Nullable List<String> filterCookiesFromHeader(
      final @Nullable List<String> headers,
      final @NotNull KeyValueCollectionBehavior behavior,
      final @Nullable List<String> additionalSensitiveCookieNames) {
    if (headers == null || behavior.getMode() == KeyValueCollectionBehavior.Mode.OFF) {
      return null;
    }

    final @NotNull List<String> filteredHeaders = new ArrayList<>();
    for (final String header : headers) {
      final @Nullable String filteredHeader =
          filterCookies(header, behavior, additionalSensitiveCookieNames);
      if (filteredHeader != null) {
        filteredHeaders.add(filteredHeader);
      }
    }
    return filteredHeaders;
  }

  public static @Nullable String filterCookies(
      final @Nullable String cookies, final @NotNull SentryOptions options) {
    if (!options.getDataCollectionResolver().isDataCollectionConfigured()) {
      return options.isSendDefaultPii() ? cookies : null;
    }
    return filterCookies(cookies, options.getDataCollectionResolver().getCookies(), null);
  }

  public static @Nullable String filterCookies(
      final @Nullable String cookies,
      final @NotNull KeyValueCollectionBehavior behavior,
      final @Nullable List<String> additionalSensitiveCookieNames) {
    if (cookies == null || behavior.getMode() == KeyValueCollectionBehavior.Mode.OFF) {
      return null;
    }

    final @NotNull String[] cookieValues = cookies.split(";", -1);
    final @NotNull StringBuilder filteredCookies = new StringBuilder();
    for (int i = 0; i < cookieValues.length; i++) {
      if (i > 0) {
        filteredCookies.append(';');
      }
      filteredCookies.append(
          filterCookie(cookieValues[i], behavior, additionalSensitiveCookieNames));
    }
    return filteredCookies.toString();
  }

  public static @Nullable String filterSetCookie(
      final @Nullable String cookie, final @NotNull SentryOptions options) {
    if (!options.getDataCollectionResolver().isDataCollectionConfigured()) {
      return options.isSendDefaultPii() ? cookie : null;
    }
    return filterSetCookie(cookie, options.getDataCollectionResolver().getCookies());
  }

  public static @Nullable String filterSetCookie(
      final @Nullable String cookie, final @NotNull KeyValueCollectionBehavior behavior) {
    if (cookie == null || behavior.getMode() == KeyValueCollectionBehavior.Mode.OFF) {
      return null;
    }

    final int attributesSeparator = cookie.indexOf(';');
    final @NotNull String cookieValue =
        attributesSeparator < 0 ? cookie : cookie.substring(0, attributesSeparator);
    if (!isValidCookiePair(cookieValue)) {
      return SENSITIVE_DATA_SUBSTITUTE;
    }
    final @NotNull String attributes =
        attributesSeparator < 0 ? "" : cookie.substring(attributesSeparator);
    return filterCookie(cookieValue, behavior, null) + attributes;
  }

  private static @NotNull String filterCookie(
      final @NotNull String cookie,
      final @NotNull KeyValueCollectionBehavior behavior,
      final @Nullable List<String> additionalSensitiveCookieNames) {
    if (cookie.trim().isEmpty()) {
      return cookie;
    }

    if (!isValidCookiePair(cookie)) {
      return SENSITIVE_DATA_SUBSTITUTE;
    }

    final int separator = cookie.indexOf('=');
    final @NotNull String name = cookie.substring(0, separator);
    final @NotNull String normalizedName = name.trim();
    final boolean sensitive =
        HttpUtils.containsSensitiveDataKey(normalizedName)
            || isSecurityCookie(normalizedName, additionalSensitiveCookieNames);
    final boolean matchesTerm = HttpUtils.containsTerm(normalizedName, behavior.getTerms());
    final boolean shouldFilter =
        sensitive
            || (behavior.getMode() == KeyValueCollectionBehavior.Mode.DENY_LIST && matchesTerm)
            || (behavior.getMode() == KeyValueCollectionBehavior.Mode.ALLOW_LIST && !matchesTerm);

    if (shouldFilter) {
      return name + "=" + SENSITIVE_DATA_SUBSTITUTE;
    }
    return cookie;
  }

  private static boolean isValidCookiePair(final @NotNull String cookie) {
    final @NotNull String cookiePair = cookie.trim();
    final int separator = cookiePair.indexOf('=');
    if (separator <= 0 || !isValidCookieName(cookiePair.substring(0, separator))) {
      return false;
    }

    final @NotNull String value = cookiePair.substring(separator + 1);
    int start = 0;
    int end = value.length();
    if (!value.isEmpty() && value.charAt(0) == '"') {
      if (value.length() < 2 || value.charAt(value.length() - 1) != '"') {
        return false;
      }
      start++;
      end--;
    }

    for (int i = start; i < end; i++) {
      if (!isCookieOctet(value.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  private static boolean isValidCookieName(final @NotNull String name) {
    for (int i = 0; i < name.length(); i++) {
      if (!isCookieNameCharacter(name.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  private static boolean isCookieNameCharacter(final char value) {
    if ((value >= 'a' && value <= 'z')
        || (value >= 'A' && value <= 'Z')
        || (value >= '0' && value <= '9')) {
      return true;
    }

    switch (value) {
      case '!':
      case '#':
      case '$':
      case '%':
      case '&':
      case '\'':
      case '*':
      case '+':
      case '-':
      case '.':
      case '^':
      case '_':
      case '`':
      case '|':
      case '~':
        return true;
      default:
        return false;
    }
  }

  private static boolean isCookieOctet(final char value) {
    return value == 0x21
        || (value >= 0x23 && value <= 0x2B)
        || (value >= 0x2D && value <= 0x3A)
        || (value >= 0x3C && value <= 0x5B)
        || (value >= 0x5D && value <= 0x7E);
  }

  public static @Nullable List<String> filterOutSecurityCookiesFromHeader(
      final @Nullable Enumeration<String> headers,
      final @Nullable String headerName,
      final @Nullable List<String> additionalCookieNamesToFilter) {
    if (headers == null) {
      return null;
    }

    return filterOutSecurityCookiesFromHeader(
        Collections.list(headers), headerName, additionalCookieNamesToFilter);
  }

  public static @Nullable List<String> filterOutSecurityCookiesFromHeader(
      final @Nullable List<String> headers,
      final @Nullable String headerName,
      final @Nullable List<String> additionalCookieNamesToFilter) {
    if (headers == null) {
      return null;
    }

    if (headerName != null && !COOKIE_HEADER_NAME.equalsIgnoreCase(headerName)) {
      return headers;
    }

    final @NotNull ArrayList<String> filteredHeaders = new ArrayList<>();
    for (final String header : headers) {
      filteredHeaders.add(filterOutSecurityCookies(header, additionalCookieNamesToFilter));
    }
    return filteredHeaders;
  }

  public static @Nullable String filterOutSecurityCookies(
      final @Nullable String cookieString,
      final @Nullable List<String> additionalCookieNamesToFilter) {
    if (cookieString == null) {
      return null;
    }

    final @NotNull String[] cookies = cookieString.split(";", -1);
    final @NotNull StringBuilder filteredCookieString = new StringBuilder();
    boolean isFirst = true;
    for (String cookie : cookies) {
      if (!isFirst) {
        filteredCookieString.append(";");
      }

      final @NotNull String[] cookieParts = cookie.split("=", -1);
      final @NotNull String cookieName = cookieParts[0];
      if (isSecurityCookie(cookieName.trim(), additionalCookieNamesToFilter)) {
        filteredCookieString.append(cookieName + "=" + SENSITIVE_DATA_SUBSTITUTE);
      } else {
        filteredCookieString.append(cookie);
      }
      isFirst = false;
    }
    return filteredCookieString.toString();
  }

  public static boolean isSecurityCookie(
      final @NotNull String cookieName,
      final @Nullable List<String> additionalCookieNamesToFilter) {
    final @NotNull String cookieNameToSearchFor = cookieName.toUpperCase(Locale.ROOT);
    if (SECURITY_COOKIES.contains(cookieNameToSearchFor)) {
      return true;
    }

    if (additionalCookieNamesToFilter != null) {
      for (String additionalCookieName : additionalCookieNamesToFilter) {
        if (additionalCookieName.toUpperCase(Locale.ROOT).equals(cookieNameToSearchFor)) {
          return true;
        }
      }
    }
    return false;
  }
}
