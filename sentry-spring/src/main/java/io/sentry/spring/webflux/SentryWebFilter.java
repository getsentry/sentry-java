package io.sentry.spring.webflux;

import io.sentry.Breadcrumb;
import io.sentry.IHub;
import io.sentry.Sentry;
import java.util.Optional;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

/** Manages {@link io.sentry.Scope} in Webflux request processing. */
@ApiStatus.Experimental
public final class SentryWebFilter implements WebFilter {
  /**
   * A key under which current {@link io.sentry.IHub} is stored in Spring Webflux {@link
   * org.springframework.web.server.ServerWebExchange}.
   */
  public static String HUB_EXCHANGE_CONTEXT_ATTRIBUTE =
      SentryWebFilter.class.getName() + ".EXCHANGE_CONTEXT";

  /**
   * A key under which current {@link io.sentry.IHub} is stored in Reactor Context {@link Context}.
   */
  public static String HUB_REACTOR_CONTEXT_ATTRIBUTE =
      SentryWebFilter.class.getName() + ".REACTOR_CONTEXT";

  private final @NotNull SentryRequestResolver sentryRequestResolver;

  public SentryWebFilter() {
    this.sentryRequestResolver = new SentryRequestResolver();
  }

  @Override
  public Mono<Void> filter(
      final @NotNull ServerWebExchange serverWebExchange,
      final @NotNull WebFilterChain webFilterChain) {
    // hub used in request execution, can be retrieved from ServerWebExchange or Reactor Context
    final IHub currentHub = Sentry.getCurrentHub().clone();
    serverWebExchange.getAttributes().put(HUB_EXCHANGE_CONTEXT_ATTRIBUTE, currentHub);
    return webFilterChain
        .filter(serverWebExchange)
        .doFinally(
            __ -> {
              final IHub hub = getHub(serverWebExchange);
              if (hub != null) {
                hub.popScope();
              }
            })
        .doFirst(
            () -> {
              final IHub hub = getHub(serverWebExchange);
              if (hub != null) {
                hub.pushScope();
                final ServerHttpRequest request = serverWebExchange.getRequest();
                hub.addBreadcrumb(
                    Breadcrumb.http(request.getURI().toString(), request.getMethodValue()));
                hub.configureScope(
                    scope ->
                        scope.setRequest(
                            sentryRequestResolver.resolveSentryRequest(hub.getOptions(), request)));
              }
            })
        .contextWrite(ctx -> ctx.put(HUB_REACTOR_CONTEXT_ATTRIBUTE, currentHub))
        .then();
  }

  private @Nullable IHub getHub(@NotNull ServerWebExchange serverWebExchange) {
    return (IHub) serverWebExchange.getAttributes().get(HUB_EXCHANGE_CONTEXT_ATTRIBUTE);
  }

  /**
   * Resolves Sentry {@link IHub} for current request execution from Reactor {@link Context}.
   *
   * @param context - reactor context
   * @return hub or empty if hub is not assigned to context
   */
  public static @NotNull Optional<IHub> getHub(ContextView context) {
    return context.getOrEmpty(HUB_REACTOR_CONTEXT_ATTRIBUTE);
  }
}
