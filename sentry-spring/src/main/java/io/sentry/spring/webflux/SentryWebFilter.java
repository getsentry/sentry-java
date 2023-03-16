package io.sentry.spring.webflux;

import static io.sentry.TypeCheckHint.WEBFLUX_FILTER_REQUEST;
import static io.sentry.TypeCheckHint.WEBFLUX_FILTER_RESPONSE;

import io.sentry.Breadcrumb;
import io.sentry.Hint;
import io.sentry.IHub;
import io.sentry.NoOpHub;
import io.sentry.Sentry;
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
  public static final String SENTRY_HUB_KEY = "sentry-hub";

  private final @NotNull SentryRequestResolver sentryRequestResolver;

  public SentryWebFilter(final @NotNull IHub hub) {
    Objects.requireNonNull(hub, "hub is required");
    this.sentryRequestResolver = new SentryRequestResolver(hub);
  }

  @Override
  public Mono<Void> filter(
      final @NotNull ServerWebExchange serverWebExchange,
      final @NotNull WebFilterChain webFilterChain) {
    @NotNull IHub requestHub = Sentry.cloneMainHub();
    return webFilterChain
        .filter(serverWebExchange)
        .doFinally(
            __ -> {
              requestHub.popScope();
              Sentry.setCurrentHub(NoOpHub.getInstance());
            })
        .doFirst(
            () -> {
              serverWebExchange.getAttributes().put(SENTRY_HUB_KEY, requestHub);
              Sentry.setCurrentHub(requestHub);
              requestHub.pushScope();
              final ServerHttpRequest request = serverWebExchange.getRequest();
              final ServerHttpResponse response = serverWebExchange.getResponse();

              final Hint hint = new Hint();
              hint.set(WEBFLUX_FILTER_REQUEST, request);
              hint.set(WEBFLUX_FILTER_RESPONSE, response);
              final String methodName =
                  request.getMethod() != null ? request.getMethod().name() : "unknown";
              requestHub.addBreadcrumb(
                  Breadcrumb.http(request.getURI().toString(), methodName), hint);
              requestHub.configureScope(
                  scope -> scope.setRequest(sentryRequestResolver.resolveSentryRequest(request)));
            });
  }
}
