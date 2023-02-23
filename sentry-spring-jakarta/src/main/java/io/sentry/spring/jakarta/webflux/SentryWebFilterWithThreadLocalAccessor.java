package io.sentry.spring.jakarta.webflux;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import io.sentry.IHub;
import reactor.core.publisher.Mono;

/** Manages {@link io.sentry.Scope} in Webflux request processing. */
@ApiStatus.Experimental
public final class SentryWebFilterWithThreadLocalAccessor extends SentryWebFilter {

  public SentryWebFilterWithThreadLocalAccessor(final @NotNull IHub hub) {
    super(hub);
  }

  @Override
  public Mono<Void> filter(
      final @NotNull ServerWebExchange serverWebExchange,
      final @NotNull WebFilterChain webFilterChain) {
    return ReactorUtils.withSentry(super.filter(serverWebExchange, webFilterChain));
  }
}
