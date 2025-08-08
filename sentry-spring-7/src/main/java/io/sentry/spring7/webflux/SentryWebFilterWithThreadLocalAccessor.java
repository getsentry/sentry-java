package io.sentry.spring7.webflux;

import io.sentry.IScope;
import io.sentry.IScopes;
import io.sentry.ITransaction;
import io.sentry.Sentry;
import io.sentry.reactor.SentryReactorUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/** Manages {@link IScope} in Webflux request processing. */
@ApiStatus.Experimental
public final class SentryWebFilterWithThreadLocalAccessor extends AbstractSentryWebFilter {

  public static final String TRACE_ORIGIN = "auto.spring_jakarta.webflux";

  public SentryWebFilterWithThreadLocalAccessor(final @NotNull IScopes scopes) {
    super(scopes);
  }

  @Override
  public Mono<Void> filter(
      final @NotNull ServerWebExchange serverWebExchange,
      final @NotNull WebFilterChain webFilterChain) {
    final @NotNull TransactionContainer transactionContainer = new TransactionContainer();
    return SentryReactorUtils.withSentryForkedRoots(
        webFilterChain
            .filter(serverWebExchange)
            .doFinally(
                __ ->
                    doFinally(
                        serverWebExchange,
                        Sentry.getCurrentScopes(),
                        transactionContainer.transaction))
            .doOnError(e -> doOnError(transactionContainer.transaction, e))
            .doFirst(
                () -> {
                  doFirst(serverWebExchange, Sentry.getCurrentScopes());
                  final ITransaction transaction =
                      maybeStartTransaction(
                          Sentry.getCurrentScopes(), serverWebExchange.getRequest(), TRACE_ORIGIN);
                  transactionContainer.transaction = transaction;
                }));
  }

  private static class TransactionContainer {
    private volatile @Nullable ITransaction transaction;
  }
}
