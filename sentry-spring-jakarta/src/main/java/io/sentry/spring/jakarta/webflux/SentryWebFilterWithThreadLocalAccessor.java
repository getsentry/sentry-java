package io.sentry.spring.jakarta.webflux;

import io.sentry.IHub;
import io.sentry.NoOpHub;
import io.sentry.Sentry;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/** Manages {@link io.sentry.Scope} in Webflux request processing. */
@ApiStatus.Experimental
public final class SentryWebFilterWithThreadLocalAccessor extends AbstractSentryWebFilter {

  public SentryWebFilterWithThreadLocalAccessor(final @NotNull IHub hub) {
    super(hub);
  }

  @Override
  public Mono<Void> filter(
      final @NotNull ServerWebExchange serverWebExchange,
      final @NotNull WebFilterChain webFilterChain) {
    return ReactorUtils.withSentryNewMainHubClone(
        webFilterChain
            .filter(serverWebExchange)
            .doFinally(
                __ -> {
                  doFinally(Sentry.getCurrentHub());
                  Sentry.setCurrentHub(NoOpHub.getInstance());
                })
            .doFirst(
                () -> {
                  doFirst(serverWebExchange, Sentry.getCurrentHub());
                }));
  }
}
