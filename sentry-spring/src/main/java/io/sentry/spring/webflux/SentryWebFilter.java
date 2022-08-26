package io.sentry.spring.webflux;

import io.sentry.Sentry;
import static io.sentry.TypeCheckHint.WEBFLUX_FILTER_REQUEST;
import static io.sentry.TypeCheckHint.WEBFLUX_FILTER_RESPONSE;

import io.sentry.Breadcrumb;
import io.sentry.Hint;
import io.sentry.IHub;
import io.sentry.util.Objects;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import java.util.UUID;

import reactor.core.publisher.Mono;

/** Manages {@link io.sentry.Scope} in Webflux request processing. */
@ApiStatus.Experimental
public final class SentryWebFilter implements WebFilter {
  public static final String SENTRY_HUB_KEY = "sentry-hub";
  private final @NotNull SentryRequestResolver sentryRequestResolver;

  public SentryWebFilter(final @NotNull IHub hub) {
    this.sentryRequestResolver = new SentryRequestResolver(hub.getOptions().isSendDefaultPii());
  }

  @Override
  public Mono<Void> filter(
      final @NotNull ServerWebExchange serverWebExchange,
      final @NotNull WebFilterChain webFilterChain) {
    @NotNull final String hubId = UUID.randomUUID().toString();
    @NotNull final IHub hub = Sentry.getHub(hubId);

    serverWebExchange.getAttributes().put(SENTRY_HUB_KEY, hub);

    return webFilterChain
        .filter(serverWebExchange)
      .contextWrite(ctx -> {
        return ctx;
      })
        .doFinally(
            __ -> {
              hub.popScope();
              Sentry.clearHub(hubId);
            })
        .doFirst(
            () -> {
              hub.pushScope();
              final ServerHttpRequest request = serverWebExchange.getRequest();
              final ServerHttpResponse response = serverWebExchange.getResponse();

              final Hint hint = new Hint();
              hint.set(WEBFLUX_FILTER_REQUEST, request);
              hint.set(WEBFLUX_FILTER_RESPONSE, response);

              hub.addBreadcrumb(
                  Breadcrumb.http(request.getURI().toString(), request.getMethodValue()), hint);
              hub.configureScope(
                  scope -> scope.setRequest(sentryRequestResolver.resolveSentryRequest(request)));
            })
      .contextWrite(ctx -> ctx.put(SENTRY_HUB_KEY, hub));
  }
}
