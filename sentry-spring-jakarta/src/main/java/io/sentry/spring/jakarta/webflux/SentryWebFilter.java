package io.sentry.spring.jakarta.webflux;

import com.jakewharton.nopen.annotation.Open;
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
@Open
public class SentryWebFilter extends AbstractSentryWebFilter {

  public SentryWebFilter(final @NotNull IHub hub) {
    super(hub);
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
              doFinally(requestHub);
              Sentry.setCurrentHub(NoOpHub.getInstance());
            })
        .doFirst(
            () -> {
              Sentry.setCurrentHub(requestHub);
              doFirst(serverWebExchange, requestHub);
            });
  }
}
