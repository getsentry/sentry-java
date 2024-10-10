package io.sentry.util;

import static io.sentry.util.UrlUtils.SENSITIVE_DATA_SUBSTITUTE;

import io.sentry.HttpStatusCodeRange;
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
