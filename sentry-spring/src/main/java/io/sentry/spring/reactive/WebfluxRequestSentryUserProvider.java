package io.sentry.spring.reactive;

import io.sentry.SentryOptions;
import io.sentry.protocol.User;
import io.sentry.util.Objects;
import java.security.Principal;
import javax.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.server.ServerWebExchange;

/**
 * Resolves user information from {@link HttpServletRequest} obtained via {@link
 * RequestContextHolder}.
 */
public final class WebfluxRequestSentryUserProvider implements SentryReactiveUserProvider {
  static final String REQUEST_PRINCIPAL_ATTR_NAME =
      "WebfluxRequestSentryUserProvider.REQUEST_PRINCIPAL_ATTR_NAME";

  private final @NotNull SentryOptions options;

  public WebfluxRequestSentryUserProvider(final @NotNull SentryOptions options) {
    this.options = Objects.requireNonNull(options, "options are required");
  }

  @Override
  public @Nullable User provideUser(ServerWebExchange request) {
    if (options.isSendDefaultPii()) {
      final User user = new User();
      user.setIpAddress(toIpAddress(request.getRequest()));
      final Object principal = request.getAttributes().get(REQUEST_PRINCIPAL_ATTR_NAME);
      if (principal instanceof Principal) {
        user.setUsername(((Principal) principal).getName());
      }
    }
    return null;
  }

  private static @NotNull String toIpAddress(final @NotNull ServerHttpRequest request) {
    return request.getHeaders().getValuesAsList("X-FORWARDED-FOR").stream()
        .findFirst()
        .orElseGet(() -> request.getRemoteAddress().toString());
  }
}
