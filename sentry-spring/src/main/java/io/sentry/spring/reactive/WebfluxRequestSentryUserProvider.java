package io.sentry.spring.reactive;

import static reactor.core.publisher.Mono.fromSupplier;

import io.sentry.SentryOptions;
import io.sentry.protocol.User;
import io.sentry.util.Objects;
import java.security.Principal;
import javax.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

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
  public Mono<User> provideUser(ServerWebExchange exchange) {
    if (options.isSendDefaultPii()) {
      return exchange
          .getPrincipal()
          .map(principal -> requestUser(exchange, principal))
          .switchIfEmpty(fromSupplier(() -> requestUser(exchange)));
    }
    return Mono.empty();
  }

  private static User requestUser(ServerWebExchange exchange) {
    final User user = new User();
    user.setIpAddress(toIpAddress(exchange.getRequest()));
    return user;
  }

  private static User requestUser(ServerWebExchange exchange, Principal principal) {
    final User user = requestUser(exchange);
    user.setUsername(principal.getName());
    return user;
  }

  private static @NotNull String toIpAddress(final @NotNull ServerHttpRequest request) {
    return request.getHeaders().getValuesAsList("X-FORWARDED-FOR").stream()
        .findFirst()
        .orElseGet(() -> request.getRemoteAddress().toString());
  }
}
