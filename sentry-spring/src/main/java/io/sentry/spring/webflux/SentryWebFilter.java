package io.sentry.spring.webflux;

import static io.sentry.TypeCheckHint.WEBFLUX_FILTER_REQUEST;
import static io.sentry.TypeCheckHint.WEBFLUX_FILTER_RESPONSE;

import io.sentry.Breadcrumb;
import io.sentry.IHub;
import io.sentry.util.Objects;
import java.util.HashMap;
import java.util.Map;
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

              final Map<String, Object> hintMap = new HashMap<>();
              hintMap.put(WEBFLUX_FILTER_REQUEST, request);
              hintMap.put(WEBFLUX_FILTER_RESPONSE, response);

              hub.addBreadcrumb(
                  Breadcrumb.http(request.getURI().toString(), request.getMethodValue()), hintMap);
              hub.configureScope(
                  scope -> scope.setRequest(sentryRequestResolver.resolveSentryRequest(request)));
            });
  }
}
