package io.sentry.util;

import static io.sentry.util.UrlUtils.SENSITIVE_DATA_SUBSTITUTE;

import io.sentry.HttpStatusCodeRange;
import io.sentry.KeyValueCollectionBehavior;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class HttpUtils {

  public static final String COOKIE_HEADER_NAME = "Cookie";

  private static final List<String> SENSITIVE_HEADERS =
      Arrays.asList(
          "X-FORWARDED-FOR",
          "AUTHORIZATION",
          "COOKIE",
          "SET-COOKIE",
          "X-API-KEY",
          "X-REAL-IP",
          "REMOTE-ADDR",
          "FORWARDED",
          "PROXY-AUTHORIZATION",
          "X-CSRF-TOKEN",
          "X-CSRFTOKEN",
          "X-XSRF-TOKEN");

  private static final List<String> SENSITIVE_DATA_KEYS =
      Arrays.asList(
          "auth",
          "token",
          "secret",
          "password",
          "passwd",
          "pwd",
          "key",
          "jwt",
          "bearer",
          "sso",
          "saml",
          "csrf",
          "xsrf",
          "credentials",
          "session",
          "sid",
          "identity");

  private static final List<String> SECURITY_COOKIES =
      Arrays.asList(
          "JSESSIONID",
          "JSESSIONIDSSO",
          "JSSOSESSIONID",
          "SESSIONID",
          "SID",
          "CSRFTOKEN",
          "XSRF-TOKEN");

  private static final HttpStatusCodeRange CLIENT_ERROR_STATUS_CODES =
      new HttpStatusCodeRange(400, 499);

  private static final HttpStatusCodeRange SEVER_ERROR_STATUS_CODES =
      new HttpStatusCodeRange(500, 599);

  public static boolean containsSensitiveHeader(final @NotNull String header) {
    return SENSITIVE_HEADERS.contains(header.toUpperCase(Locale.ROOT));
  }

  public static @Nullable String filterQueryParams(
      final @Nullable String query, final @NotNull KeyValueCollectionBehavior behavior) {
    if (query == null || behavior.getMode() == KeyValueCollectionBehavior.Mode.OFF) {
      return null;
    }

    final @NotNull StringBuilder filteredQuery = new StringBuilder();
    final @NotNull String[] params = query.split("&", -1);
    for (int i = 0; i < params.length; i++) {
      if (i > 0) {
        filteredQuery.append('&');
      }

      final @NotNull String param = params[i];
      final int separator = param.indexOf('=');
      final @NotNull String name = separator < 0 ? param : param.substring(0, separator);
      final @NotNull String decodedName = decodeQueryParamName(name);
      final boolean sensitive = containsTerm(decodedName, SENSITIVE_DATA_KEYS);
      final boolean matchesTerm = containsTerm(decodedName, behavior.getTerms());
      final boolean shouldFilter =
          sensitive
              || (behavior.getMode() == KeyValueCollectionBehavior.Mode.DENY_LIST && matchesTerm)
              || (behavior.getMode() == KeyValueCollectionBehavior.Mode.ALLOW_LIST && !matchesTerm);

      filteredQuery.append(name);
      if (shouldFilter) {
        filteredQuery.append('=').append(SENSITIVE_DATA_SUBSTITUTE);
      } else if (separator >= 0) {
        filteredQuery.append(param.substring(separator));
      }
    }
    return filteredQuery.toString();
  }

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
      filteredHeaders.add(filterCookies(header, behavior, additionalSensitiveCookieNames));
    }
    return filteredHeaders;
  }

  public static @Nullable String filterCookies(
      final @Nullable String cookies,
      final @NotNull KeyValueCollectionBehavior behavior,
      final @Nullable List<String> additionalSensitiveCookieNames) {
    if (cookies == null || behavior.getMode() == KeyValueCollectionBehavior.Mode.OFF) {
      return null;
    }

    try {
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
    } catch (Throwable ignored) {
      return SENSITIVE_DATA_SUBSTITUTE;
    }
  }

  public static @Nullable String filterSetCookie(
      final @Nullable String cookie, final @NotNull KeyValueCollectionBehavior behavior) {
    if (cookie == null || behavior.getMode() == KeyValueCollectionBehavior.Mode.OFF) {
      return null;
    }

    try {
      final int attributesSeparator = cookie.indexOf(';');
      final @NotNull String cookieValue =
          attributesSeparator < 0 ? cookie : cookie.substring(0, attributesSeparator);
      if (!isValidCookiePair(cookieValue)) {
        return SENSITIVE_DATA_SUBSTITUTE;
      }
      final @NotNull String attributes =
          attributesSeparator < 0 ? "" : cookie.substring(attributesSeparator);
      return filterCookie(cookieValue, behavior, null) + attributes;
    } catch (Throwable ignored) {
      return SENSITIVE_DATA_SUBSTITUTE;
    }
  }

  private static @NotNull String filterCookie(
      final @NotNull String cookie,
      final @NotNull KeyValueCollectionBehavior behavior,
      final @Nullable List<String> additionalSensitiveCookieNames) {
    if (!isValidCookiePair(cookie)) {
      return SENSITIVE_DATA_SUBSTITUTE;
    }

    final int separator = cookie.indexOf('=');
    final @NotNull String name = cookie.substring(0, separator);
    final @NotNull String normalizedName = name.trim();
    final boolean sensitive =
        containsTerm(normalizedName, SENSITIVE_DATA_KEYS)
            || isSecurityCookie(normalizedName, additionalSensitiveCookieNames);
    final boolean matchesTerm = containsTerm(normalizedName, behavior.getTerms());
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

  public static @NotNull Map<String, String> filterHeaders(
      final @NotNull Map<String, String> headers,
      final @NotNull KeyValueCollectionBehavior behavior) {
    final @NotNull Map<String, String> filteredHeaders = new LinkedHashMap<>();
    if (behavior.getMode() == KeyValueCollectionBehavior.Mode.OFF) {
      return filteredHeaders;
    }

    for (final Map.Entry<String, String> header : headers.entrySet()) {
      final @NotNull String name = header.getKey();
      final boolean sensitive =
          containsTerm(name, SENSITIVE_DATA_KEYS)
              || "Cookie".equalsIgnoreCase(name)
              || "Set-Cookie".equalsIgnoreCase(name);
      if (sensitive) {
        filteredHeaders.put(name, SENSITIVE_DATA_SUBSTITUTE);
      } else {
        final boolean matchesTerm = containsTerm(name, behavior.getTerms());
        final boolean shouldFilter =
            behavior.getMode() == KeyValueCollectionBehavior.Mode.DENY_LIST
                ? matchesTerm
                : !matchesTerm;
        filteredHeaders.put(name, shouldFilter ? SENSITIVE_DATA_SUBSTITUTE : header.getValue());
      }
    }
    return filteredHeaders;
  }

  private static @NotNull String decodeQueryParamName(final @NotNull String name) {
    try {
      return URLDecoder.decode(name, "UTF-8");
    } catch (Throwable ignored) {
      return name;
    }
  }

  private static boolean containsTerm(
      final @NotNull String key, final @NotNull List<String> terms) {
    final @NotNull String normalizedKey = key.toLowerCase(Locale.ROOT);
    for (final String term : terms) {
      if (term != null
          && !term.isEmpty()
          && normalizedKey.contains(term.toLowerCase(Locale.ROOT))) {
        return true;
      }
    }
    return false;
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

    if (headerName != null && !"Cookie".equalsIgnoreCase(headerName)) {
      return headers;
    }

    final @NotNull ArrayList<String> filteredHeaders = new ArrayList<>();

    for (final String header : headers) {
      filteredHeaders.add(
          HttpUtils.filterOutSecurityCookies(header, additionalCookieNamesToFilter));
    }

    return filteredHeaders;
  }

  public static @Nullable String filterOutSecurityCookies(
      final @Nullable String cookieString,
      final @Nullable List<String> additionalCookieNamesToFilter) {
    if (cookieString == null) {
      return null;
    }
    try {
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
    } catch (Throwable t) {
      return null;
    }
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

  public static boolean isHttpClientError(final int statusCode) {
    return CLIENT_ERROR_STATUS_CODES.isInRange(statusCode);
  }

  public static boolean isHttpServerError(final int statusCode) {
    return SEVER_ERROR_STATUS_CODES.isInRange(statusCode);
  }
}
