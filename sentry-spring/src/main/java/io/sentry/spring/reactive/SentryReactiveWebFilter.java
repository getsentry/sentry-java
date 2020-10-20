package io.sentry.spring.reactive;

import static io.sentry.spring.reactive.SentryReactiveWebHelper.REQUEST_HUB_ATTR_NAME;
import static io.sentry.spring.reactive.WebfluxRequestSentryUserProvider.REQUEST_PRINCIPAL_ATTR_NAME;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.Breadcrumb;
import io.sentry.IHub;
import io.sentry.SentryOptions;
import io.sentry.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Associates a cloned {@link io.sentry.IHub} to each incoming reactive HTTP request
 * (ServerWebExchange).
 */
@Open
public class SentryReactiveWebFilter implements WebFilter, Ordered {

  private final @NotNull IHub baseHub;
  private final @NotNull SentryOptions options;

  public SentryReactiveWebFilter(final @NotNull IHub hub, final @NotNull SentryOptions options) {
    this.baseHub = Objects.requireNonNull(hub, "hub is required");
    this.options = Objects.requireNonNull(options, "options are required");
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    final IHub hub = baseHub.clone();
    hub.pushScope();

    final ServerHttpRequest request = exchange.getRequest();
    hub.addBreadcrumb(Breadcrumb.http(request.getPath().value(), request.getMethodValue()));

    hub.configureScope(
        scope -> {
          scope.addEventProcessor(new SentryReactiveWebRequestProcessor(request, options));
        });

    exchange.getAttributes().put(REQUEST_HUB_ATTR_NAME, hub);
    return exchange
        .getPrincipal()
        .doOnNext(principal -> exchange.getAttributes().put(REQUEST_PRINCIPAL_ATTR_NAME, principal))
        .then(chain.filter(exchange))
        .doFinally(_signal -> hub.popScope());
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }
}
