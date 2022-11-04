package io.sentry.spring.jakarta;

import io.sentry.SentryOptions;
import io.sentry.protocol.User;
import io.sentry.util.Objects;
import jakarta.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Resolves user information from {@link HttpServletRequest} obtained via {@link
 * RequestContextHolder}.
 */
public final class HttpServletRequestSentryUserProvider implements SentryUserProvider {
  private final @NotNull SentryOptions options;

  public HttpServletRequestSentryUserProvider(final @NotNull SentryOptions options) {
    this.options = Objects.requireNonNull(options, "options are required");
  }

  @Override
  public @Nullable User provideUser() {
    if (options.isSendDefaultPii()) {
      final RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
      if (requestAttributes instanceof ServletRequestAttributes) {
        final ServletRequestAttributes servletRequestAttributes =
            (ServletRequestAttributes) requestAttributes;
        final HttpServletRequest request = servletRequestAttributes.getRequest();

        final User user = new User();
        user.setIpAddress(toIpAddress(request));
        if (request.getUserPrincipal() != null) {
          user.setUsername(request.getUserPrincipal().getName());
        }
        return user;
      }
    }
    return null;
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
