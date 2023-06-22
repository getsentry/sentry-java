package io.sentry.spring.jakarta.webflux;

import io.sentry.IHub;
import io.sentry.ITransaction;
import io.sentry.Sentry;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
    final @NotNull TransactionContainer transactionContainer = new TransactionContainer();
    return ReactorUtils.withSentryNewMainHubClone(
        webFilterChain
            .filter(serverWebExchange)
            .doFinally(
                __ ->
                    doFinally(
                        serverWebExchange,
                        Sentry.getCurrentHub(),
                        transactionContainer.transaction))
            .doOnError(e -> doOnError(transactionContainer.transaction, e))
            .doFirst(
                () -> {
                  doFirst(serverWebExchange, Sentry.getCurrentHub());
                  final ITransaction transaction =
                      maybeStartTransaction(Sentry.getCurrentHub(), serverWebExchange.getRequest());
                  transactionContainer.transaction = transaction;
                  if(transaction != null) {
                    transaction.getSpanContext().setOrigin("auto.spring_jakarta.webflux");
                  }
                }));
  }

  private static class TransactionContainer {
    private volatile @Nullable ITransaction transaction;
  }
}
