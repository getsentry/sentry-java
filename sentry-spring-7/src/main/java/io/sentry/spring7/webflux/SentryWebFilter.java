package io.sentry.spring7.webflux;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.IScope;
import io.sentry.IScopes;
import io.sentry.ITransaction;
import io.sentry.Sentry;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/** Manages {@link IScope} in Webflux request processing. */
@ApiStatus.Experimental
@Open
public class SentryWebFilter extends AbstractSentryWebFilter {

  private static final String TRACE_ORIGIN = "auto.spring7.webflux";

  public SentryWebFilter(final @NotNull IScopes scopes) {
    super(scopes);
  }

  @Override
  public Mono<Void> filter(
      final @NotNull ServerWebExchange serverWebExchange,
      final @NotNull WebFilterChain webFilterChain) {
    @NotNull IScopes requestScopes = Sentry.forkedRootScopes("request.webflux");
    final ServerHttpRequest request = serverWebExchange.getRequest();
    final @Nullable ITransaction transaction =
        maybeStartTransaction(requestScopes, request, TRACE_ORIGIN);
    return webFilterChain
        .filter(serverWebExchange)
        .doFinally(__ -> doFinally(serverWebExchange, requestScopes, transaction))
        .doOnError(e -> doOnError(transaction, e))
        .doFirst(
            () -> {
              Sentry.setCurrentScopes(requestScopes);
              doFirst(serverWebExchange, requestScopes);
            });
  }
}
