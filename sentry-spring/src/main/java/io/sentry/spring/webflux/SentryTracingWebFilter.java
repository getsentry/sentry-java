package io.sentry.spring.webflux;

import io.sentry.*;
import io.sentry.exception.InvalidSentryTraceHeaderException;
import io.sentry.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.pattern.PathPattern;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.Optional;

/**
 * Creates {@link ITransaction} around HTTP request executions.
 *
 * <p>Only requests that have {@link HandlerMapping#BEST_MATCHING_PATTERN_ATTRIBUTE} request
 * attribute set are turned into transactions. This attribute is set in {@link
 * RequestMappingInfoHandlerMapping} on request that have not been dropped with any {@link
 * javax.servlet.Filter}.
 */
public class SentryTracingWebFilter implements WebFilter {
  /**
   * Operation used by {@link io.sentry.spring.tracing.SentryTransaction} created in {@link SentryTransactionWebFilter}.
   */
  private static final String TRANSACTION_OP = "http.server";

  //    private final @NotNull TransactionNameProvider transactionNameProvider;
  private final @NotNull
  IHub hub;

  public SentryTransactionWebFilterJava() {
    this(HubAdapter.getInstance());
  }

  public SentryTransactionWebFilterJava(
    final @NotNull IHub hub
//        final @NotNull TransactionNameProvider transactionNameProvider
  ) {
    this.hub = Objects.requireNonNull(hub, "hub is required");
//        this.transactionNameProvider =
//            Objects.requireNonNull(transactionNameProvider, "transactionNameProvider is required");
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    if (!hub.isEnabled()) {
      return chain.filter(exchange);
    }

    ServerHttpRequest request = exchange.getRequest();

    final String sentryTraceHeader = request.getHeaders().getFirst(SentryTraceHeader.SENTRY_TRACE_HEADER);

    // at this stage we are not able to get real transaction name
    final ITransaction transaction = startTransaction(request, sentryTraceHeader);

    return chain.filter(exchange)
      .doFinally(signalType -> {
        // after all filters run, templated path pattern is available in request attribute
        final String transactionName = provideTransactionName(exchange);
        // if transaction name is not resolved, the request has not been processed by a controller
        // and we should not report it to Sentry
        if (transactionName != null) {
          transaction.setName(transactionName);
          transaction.setOperation(TRANSACTION_OP);
          // if exception has been thrown, transaction status is already set to INTERNAL_ERROR, and
          // httpResponse.getStatus() returns 200.
          if (transaction.getStatus() == null) {
            transaction.setStatus(SpanStatus.fromHttpStatusCode(
              Optional.ofNullable(exchange.getResponse().getRawStatusCode()).orElse(0)
            ));
          }
          transaction.finish();
        }
      })
      .doOnError(error -> {
        // exceptions that are not handled by Spring
        transaction.setStatus(SpanStatus.INTERNAL_ERROR);
        transaction.setThrowable(error);
      }).contextWrite(Context.of("sentryTransaction", transaction));
  }

  private ITransaction startTransaction(
    final @NotNull ServerHttpRequest request, final @Nullable String sentryTraceHeader) {

    final String name = request.getMethod() + " " + request.getURI();

    final CustomSamplingContext customSamplingContext = new CustomSamplingContext();
    customSamplingContext.set("request", request);

    if (sentryTraceHeader != null) {
      try {
        final TransactionContext contexts =
          TransactionContext.fromSentryTrace(
            name, "http.server", new SentryTraceHeader(sentryTraceHeader));
        return hub.startTransaction(contexts, customSamplingContext, true);
      } catch (InvalidSentryTraceHeaderException e) {
        hub.getOptions()
          .getLogger()
          .log(SentryLevel.DEBUG, e, "Failed to parse Sentry trace header: %s", e.getMessage());
      }
    }
    return hub.startTransaction(name, "http.server", customSamplingContext, true);
  }

  private @Nullable
  String provideTransactionName(
    ServerWebExchange serverWebExchange
  ) {
    PathPattern pattern = serverWebExchange.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    if (pattern != null) {
      return serverWebExchange.getRequest().getMethodValue() + " " + pattern.getPatternString();
    }

    return null;
  }
}
