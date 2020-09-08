package io.sentry.spring;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.core.IHub;
import io.sentry.core.SentryOptions;
import io.sentry.core.util.Objects;
import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Adds {@link SentryUserHttpServletRequestProcessor} to the scope in order to decorate {@link
 * io.sentry.core.SentryEvent} with principal name and user ip address.
 */
@Open
public class SentrySecurityFilter extends OncePerRequestFilter {
  private final @NotNull IHub hub;
  private final @NotNull SentryOptions options;

  public SentrySecurityFilter(final @NotNull IHub hub, final @NotNull SentryOptions options) {
    this.hub = Objects.requireNonNull(hub, "hub is required");
    this.options = Objects.requireNonNull(options, "options are required");
  }

  @Override
  protected void doFilterInternal(
      final @NotNull HttpServletRequest request,
      final @NotNull HttpServletResponse response,
      final @NotNull FilterChain filterChain)
      throws ServletException, IOException {
    hub.configureScope(
        scope ->
            scope.addEventProcessor(
                new SentryUserHttpServletRequestProcessor(
                    request.getUserPrincipal(), toIpAddress(request), options)));
    filterChain.doFilter(request, response);
  }

  // it is advised to not use `String#split` method but since we do not have 3rd party libraries
  // this is our only option.
  @SuppressWarnings("StringSplitter")
  private static @NotNull String toIpAddress(final @NotNull HttpServletRequest request) {
    final String ipAddress = request.getHeader("X-FORWARDED-FOR");
    if (ipAddress != null) {
      return ipAddress.contains(",") ? ipAddress.split(",")[0].trim() : ipAddress;
    } else {
      return request.getRemoteAddr();
    }
  }
}
