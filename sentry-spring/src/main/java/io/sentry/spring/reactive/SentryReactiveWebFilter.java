package io.sentry.spring.reactive;

import static io.sentry.spring.reactive.SentryReactiveHubContextHolder.*;
import static io.sentry.spring.reactive.SentryReactiveWebHelper.REQUEST_HUB_ATTR_NAME;
import static io.sentry.spring.reactive.WebfluxRequestSentryUserProvider.USER_ATTR;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.Breadcrumb;
import io.sentry.IHub;
import io.sentry.SentryOptions;
import io.sentry.util.Objects;
import java.util.List;
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
  // Ensure that this filter is called after spring-security filters (order -100)
  private static final int WEB_FILTER_ORDER = -90;

  private final @NotNull IHub baseHub;
  private final @NotNull SentryOptions options;
  private final @NotNull List<SentryReactiveUserProvider> sentryUserProviders;

  public SentryReactiveWebFilter(
      final @NotNull IHub hub,
      final @NotNull SentryOptions options,
      final @NotNull List<SentryReactiveUserProvider> sentryUserProviders) {
    this.baseHub = Objects.requireNonNull(hub, "hub is required");
    this.options = Objects.requireNonNull(options, "options are required");
    this.sentryUserProviders = Objects.requireNonNull(sentryUserProviders, "options are required");
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    final IHub hub = baseHub.clone();
    hub.pushScope();

    final SentryReactiveHubAdapter hubAdapter =
        new SentryReactiveHubAdapter(hub, sentryUserProviders, exchange);

    final ServerHttpRequest request = exchange.getRequest();
    hub.addBreadcrumb(Breadcrumb.http(request.getPath().value(), request.getMethodValue()));

    hub.configureScope(
        scope -> {
          scope.addEventProcessor(new SentryReactiveWebRequestProcessor(request, options));
        });

    exchange.getAttributes().put(REQUEST_HUB_ATTR_NAME, hubAdapter);

    return exchange
        .getPrincipal()
        .doOnNext(principal -> exchange.getAttributes().put(USER_ATTR, principal))
        .then(
            chain
                .filter(exchange)
                .subscriberContext(withSentryHub(hubAdapter))
                .doFinally(_signal -> hub.popScope()));
  }

  @Override
  public int getOrder() {
    return WEB_FILTER_ORDER;
  }
}
