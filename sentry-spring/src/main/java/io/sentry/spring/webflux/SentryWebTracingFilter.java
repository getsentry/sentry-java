package io.sentry.spring.webflux;

import com.jakewharton.nopen.annotation.Open;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import io.sentry.CustomSamplingContext;
import io.sentry.IHub;
import io.sentry.ITransaction;
import io.sentry.Sentry;
import io.sentry.SpanStatus;
import io.sentry.TransactionContext;
import io.sentry.TransactionOptions;
import io.sentry.protocol.TransactionNameSource;
import static io.sentry.spring.webflux.SentryWebFilter.SENTRY_HUB_KEY;
import reactor.core.publisher.Mono;

@Open
public class SentryWebTracingFilter implements WebFilter {

  private static final String TRANSACTION_OP = "http.server";

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    final @Nullable Object hubObject = exchange.getAttributes().getOrDefault(SENTRY_HUB_KEY, null);
    // TODO not use currentHub as fallback?
    final @NotNull IHub hub = hubObject == null ? Sentry.getCurrentHub() : (IHub) hubObject;

    final @NotNull ITransaction transaction = startTransaction(hub, exchange);

    return chain.filter(exchange)
      .doFinally(__ -> {
        String transactionName = TransactionNameProvider.provideTransactionName(exchange);
        if (transactionName != null) {
          transaction.setName(transactionName, TransactionNameSource.ROUTE);
          transaction.setOperation(TRANSACTION_OP);
        }
        if (transaction.getStatus() == null) {
          final @Nullable ServerHttpResponse response = exchange.getResponse();
          if (response != null) {
            final @Nullable Integer rawStatusCode = response.getRawStatusCode();
            if (rawStatusCode != null) {
              transaction.setStatus(SpanStatus.fromHttpStatusCode(rawStatusCode));
            }
          }
        }
        transaction.finish();
      })
      .doOnError(e -> {
        transaction.setStatus(SpanStatus.INTERNAL_ERROR);
        transaction.setThrowable(e);
      });
  }

  private @NotNull ITransaction startTransaction(@NotNull IHub hub, @NotNull ServerWebExchange exchange) {
    // TODO resume from headers including baggage support

    final @NotNull ServerHttpRequest request = exchange.getRequest();
    final @NotNull String name = request.getMethod() + " " + request.getURI().getPath();

    final @NotNull CustomSamplingContext customSamplingContext = new CustomSamplingContext();
    customSamplingContext.set("request", request);

    final @NotNull TransactionOptions transactionOptions = new TransactionOptions();
    transactionOptions.setCustomSamplingContext(customSamplingContext);
    transactionOptions.setBindToScope(true);

    return hub.startTransaction(new TransactionContext(name, TransactionNameSource.URL, "http.server"), transactionOptions);
  }
}
