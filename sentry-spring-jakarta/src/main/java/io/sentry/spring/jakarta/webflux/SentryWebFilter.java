package io.sentry.spring.jakarta.webflux;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.IHub;
import io.sentry.ITransaction;
import io.sentry.Sentry;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/** Manages {@link io.sentry.Scope} in Webflux request processing. */
@ApiStatus.Experimental
@Open
public class SentryWebFilter extends AbstractSentryWebFilter {

  private static final String TRACE_ORIGIN = "auto.spring_jakarta.webflux";

  public SentryWebFilter(final @NotNull IHub hub) {
    super(hub);
  }

  @Override
  public Mono<Void> filter(
      final @NotNull ServerWebExchange serverWebExchange,
      final @NotNull WebFilterChain webFilterChain) {
    @NotNull IHub requestHub = Sentry.cloneMainHub();
    final ServerHttpRequest request = serverWebExchange.getRequest();
    final @Nullable ITransaction transaction = maybeStartTransaction(requestHub, request);
    if (transaction != null) {
      transaction.getSpanContext().setOrigin(TRACE_ORIGIN);
    }
    return webFilterChain
        .filter(serverWebExchange)
        .doFinally(__ -> doFinally(serverWebExchange, requestHub, transaction))
        .doOnError(e -> doOnError(transaction, e))
        .doFirst(
            () -> {
              Sentry.setCurrentHub(requestHub);
              doFirst(serverWebExchange, requestHub);
            });
  }
}
