package io.sentry.spring.jakarta.webflux;

import static io.sentry.TypeCheckHint.WEBFLUX_FILTER_REQUEST;
import static io.sentry.TypeCheckHint.WEBFLUX_FILTER_RESPONSE;

import io.sentry.Breadcrumb;
import io.sentry.Hint;
import io.sentry.IHub;
import io.sentry.util.Objects;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/** Manages {@link io.sentry.Scope} in Webflux request processing. */
@ApiStatus.Experimental
public final class SentryWebFilter implements WebFilter {
  private final @NotNull IHub hub;
  private final @NotNull SentryRequestResolver sentryRequestResolver;

  public SentryWebFilter(final @NotNull IHub hub) {
    this.hub = Objects.requireNonNull(hub, "hub is required");
    this.sentryRequestResolver = new SentryRequestResolver(hub);
  }

  @Override
  public Mono<Void> filter(
      final @NotNull ServerWebExchange serverWebExchange,
      final @NotNull WebFilterChain webFilterChain) {
    return webFilterChain
        .filter(serverWebExchange)
        .doFinally(
            __ -> {
              hub.popScope();
            })
        .doFirst(
            () -> {
              hub.pushScope();
              final ServerHttpRequest request = serverWebExchange.getRequest();
              final ServerHttpResponse response = serverWebExchange.getResponse();

              final Hint hint = new Hint();
              hint.set(WEBFLUX_FILTER_REQUEST, request);
              hint.set(WEBFLUX_FILTER_RESPONSE, response);
              final String methodName =
                  request.getMethod() != null ? request.getMethod().name() : "unknown";
              hub.addBreadcrumb(Breadcrumb.http(request.getURI().toString(), methodName), hint);
              hub.configureScope(
                  scope -> scope.setRequest(sentryRequestResolver.resolveSentryRequest(request)));
            });
  }
}
