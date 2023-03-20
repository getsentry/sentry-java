package io.sentry.spring.jakarta.webflux;

import static io.sentry.spring.jakarta.webflux.AbstractSentryWebFilter.SENTRY_HUB_KEY;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.Baggage;
import io.sentry.BaggageHeader;
import io.sentry.CustomSamplingContext;
import io.sentry.IHub;
import io.sentry.ITransaction;
import io.sentry.Sentry;
import io.sentry.SentryLevel;
import io.sentry.SentryTraceHeader;
import io.sentry.SpanStatus;
import io.sentry.TransactionContext;
import io.sentry.TransactionOptions;
import io.sentry.exception.InvalidSentryTraceHeaderException;
import io.sentry.protocol.TransactionNameSource;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Open
@ApiStatus.Experimental
public class SentryWebTracingFilter implements WebFilter {

  private static final String TRANSACTION_OP = "http.server";

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    final @Nullable Object hubObject = exchange.getAttributes().getOrDefault(SENTRY_HUB_KEY, null);
    final @NotNull IHub hub = hubObject == null ? Sentry.getCurrentHub() : (IHub) hubObject;
    final @NotNull ServerHttpRequest request = exchange.getRequest();

    if (hub.isEnabled() && shouldTraceRequest(hub, request)) {
      final @NotNull ITransaction transaction = startTransaction(hub, request);

      return chain
          .filter(exchange)
          .doFinally(
              __ -> {
                String transactionName = TransactionNameProvider.provideTransactionName(exchange);
                if (transactionName != null) {
                  transaction.setName(transactionName, TransactionNameSource.ROUTE);
                  transaction.setOperation(TRANSACTION_OP);
                }
                if (transaction.getStatus() == null) {
                  final @Nullable ServerHttpResponse response = exchange.getResponse();
                  if (response != null) {
                    final @Nullable HttpStatusCode statusCode = response.getStatusCode();
                    if (statusCode != null) {
                      transaction.setStatus(SpanStatus.fromHttpStatusCode(statusCode.value()));
                    }
                  }
                }
                transaction.finish();
              })
          .doOnError(
              e -> {
                transaction.setStatus(SpanStatus.INTERNAL_ERROR);
                transaction.setThrowable(e);
              });
    } else {
      return chain.filter(exchange);
    }
  }

  private boolean shouldTraceRequest(
      final @NotNull IHub hub, final @NotNull ServerHttpRequest request) {
    return hub.getOptions().isTraceOptionsRequests()
        || !HttpMethod.OPTIONS.equals(request.getMethod());
  }

  private @NotNull ITransaction startTransaction(
      final @NotNull IHub hub, final @NotNull ServerHttpRequest request) {
    final @NotNull HttpHeaders headers = request.getHeaders();
    final @Nullable List<String> sentryTraceHeaders =
        headers.get(SentryTraceHeader.SENTRY_TRACE_HEADER);
    final @Nullable List<String> baggageHeaders = headers.get(BaggageHeader.BAGGAGE_HEADER);
    final @NotNull String name = request.getMethod() + " " + request.getURI().getPath();
    final @NotNull CustomSamplingContext customSamplingContext = new CustomSamplingContext();
    customSamplingContext.set("request", request);

    final TransactionOptions transactionOptions = new TransactionOptions();
    transactionOptions.setCustomSamplingContext(customSamplingContext);
    transactionOptions.setBindToScope(true);

    if (sentryTraceHeaders != null && sentryTraceHeaders.size() > 0) {
      final @NotNull Baggage baggage =
          Baggage.fromHeader(baggageHeaders, hub.getOptions().getLogger());
      try {
        final @NotNull TransactionContext contexts =
            TransactionContext.fromSentryTrace(
                name,
                TransactionNameSource.URL,
                TRANSACTION_OP,
                new SentryTraceHeader(sentryTraceHeaders.get(0)),
                baggage,
                null);

        return hub.startTransaction(contexts, transactionOptions);
      } catch (InvalidSentryTraceHeaderException e) {
        hub.getOptions()
            .getLogger()
            .log(SentryLevel.DEBUG, e, "Failed to parse Sentry trace header: %s", e.getMessage());
      }
    }

    return hub.startTransaction(
        new TransactionContext(name, TransactionNameSource.URL, TRANSACTION_OP),
        transactionOptions);
  }
}
