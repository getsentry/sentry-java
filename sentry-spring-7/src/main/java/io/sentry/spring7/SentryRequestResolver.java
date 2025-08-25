package io.sentry.spring7;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.IScopes;
import io.sentry.ISentryLifecycleToken;
import io.sentry.SentryLevel;
import io.sentry.protocol.Request;
import io.sentry.util.AutoClosableReentrantLock;
import io.sentry.util.HttpUtils;
import io.sentry.util.Objects;
import io.sentry.util.UrlUtils;
import jakarta.servlet.ServletContext;
import jakarta.servlet.SessionCookieConfig;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Open
public class SentryRequestResolver {
  protected static final @NotNull AutoClosableReentrantLock staticLock =
      new AutoClosableReentrantLock();
  private final @NotNull IScopes scopes;
  private volatile @Nullable List<String> extraSecurityCookies;

  public SentryRequestResolver(final @NotNull IScopes scopes) {
    this.scopes = Objects.requireNonNull(scopes, "options is required");
  }

  // httpRequest.getRequestURL() returns StringBuffer which is considered an obsolete class.
  @SuppressWarnings("JdkObsolete")
  public @NotNull Request resolveSentryRequest(final @NotNull HttpServletRequest httpRequest) {
    final Request sentryRequest = new Request();
    sentryRequest.setMethod(httpRequest.getMethod());
    final @NotNull UrlUtils.UrlDetails urlDetails =
        UrlUtils.parse(httpRequest.getRequestURL().toString());
    urlDetails.applyToRequest(sentryRequest);
    sentryRequest.setQueryString(httpRequest.getQueryString());
    final @NotNull List<String> additionalSecurityCookieNames =
        extractSecurityCookieNamesOrUseCached(httpRequest);
    sentryRequest.setHeaders(resolveHeadersMap(httpRequest, additionalSecurityCookieNames));

    if (scopes.getOptions().isSendDefaultPii()) {
      String cookieName = HttpUtils.COOKIE_HEADER_NAME;
      final @Nullable List<String> filteredHeaders =
          HttpUtils.filterOutSecurityCookiesFromHeader(
              httpRequest.getHeaders(cookieName), cookieName, additionalSecurityCookieNames);
      sentryRequest.setCookies(toString(filteredHeaders));
    }
    return sentryRequest;
  }

  @NotNull
  Map<String, String> resolveHeadersMap(
      final @NotNull HttpServletRequest request,
      final @NotNull List<String> additionalSecurityCookieNames) {
    final Map<String, String> headersMap = new HashMap<>();
    for (String headerName : Collections.list(request.getHeaderNames())) {
      // do not copy personal information identifiable headers
      if (scopes.getOptions().isSendDefaultPii()
          || !HttpUtils.containsSensitiveHeader(headerName)) {
        final @Nullable List<String> filteredHeaders =
            HttpUtils.filterOutSecurityCookiesFromHeader(
                request.getHeaders(headerName), headerName, additionalSecurityCookieNames);
        headersMap.put(headerName, toString(filteredHeaders));
      }
    }
    return headersMap;
  }

  private List<String> extractSecurityCookieNamesOrUseCached(
      final @NotNull HttpServletRequest httpRequest) {
    if (extraSecurityCookies == null) {
      try (final @NotNull ISentryLifecycleToken ignored = staticLock.acquire()) {
        if (extraSecurityCookies == null) {
          extraSecurityCookies = extractSecurityCookieNames(httpRequest);
        }
      }
    }

    return extraSecurityCookies;
  }

  private List<String> extractSecurityCookieNames(final @NotNull HttpServletRequest httpRequest) {
    try {
      final @Nullable ServletContext servletContext = httpRequest.getServletContext();
      if (servletContext != null) {
        final @Nullable SessionCookieConfig sessionCookieConfig =
            servletContext.getSessionCookieConfig();
        if (sessionCookieConfig != null) {
          final @Nullable String cookieName = sessionCookieConfig.getName();
          if (cookieName != null) {
            return Arrays.asList(cookieName);
          }
        }
      }
    } catch (Throwable t) {
      scopes
          .getOptions()
          .getLogger()
          .log(SentryLevel.WARNING, "Failed to extract session cookie name from request.", t);
    }

    return Collections.emptyList();
  }

  private static @Nullable String toString(final @Nullable List<String> list) {
    return list != null ? String.join(",", list) : null;
  }
}
