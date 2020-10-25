package io.sentry.spring.reactive;

import static java.util.Optional.ofNullable;
import static reactor.core.publisher.Mono.fromSupplier;
import static reactor.core.publisher.Mono.justOrEmpty;

import io.sentry.SentryOptions;
import io.sentry.protocol.User;
import io.sentry.util.Objects;
import java.net.InetSocketAddress;
import java.security.Principal;
import javax.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Resolves user information from {@link HttpServletRequest} obtained via {@link
 * RequestContextHolder}.
 */
public final class WebfluxRequestSentryUserProvider implements SentryReactiveUserProvider {
  public static final String USER_ATTR = "WebfluxRequestSentryUserProvider.USER_ATTR";
  private final @NotNull SentryOptions options;

  public WebfluxRequestSentryUserProvider(final @NotNull SentryOptions options) {
    this.options = Objects.requireNonNull(options, "options are required");
  }

  @Override
  public Mono<User> provideUser(final @NotNull ServerWebExchange exchange) {
    if (options.isSendDefaultPii()) {
      return justOrEmpty(exchange.<Principal>getAttribute(USER_ATTR))
          .map(principal -> requestUser(exchange, principal))
          .switchIfEmpty(fromSupplier(() -> requestUser(exchange)));
    }
    return Mono.empty();
  }

  private static User requestUser(final @NotNull ServerWebExchange exchange) {
    final User user = new User();
    user.setIpAddress(toIpAddress(exchange.getRequest()));
    return user;
  }

  private static User requestUser(
      final @NotNull ServerWebExchange exchange, final @NotNull Principal principal) {
    final User user = requestUser(exchange);
    user.setUsername(principal.getName());
    return user;
  }

  private static @Nullable String toIpAddress(final @NotNull ServerHttpRequest request) {
    return request.getHeaders().getValuesAsList("X-FORWARDED-FOR").stream()
        .findFirst()
        .orElseGet(
            () ->
                ofNullable(request.getRemoteAddress())
                    .map(InetSocketAddress::getHostString)
                    .orElse(null));
  }
}
