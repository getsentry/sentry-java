package io.sentry.spring.webflux;

import static io.sentry.TypeCheckHint.WEBFLUX_FILTER_REQUEST;
import static io.sentry.TypeCheckHint.WEBFLUX_FILTER_RESPONSE;

import io.sentry.Baggage;
import io.sentry.BaggageHeader;
import io.sentry.Breadcrumb;
import io.sentry.CustomSamplingContext;
import io.sentry.Hint;
import io.sentry.IHub;
import io.sentry.ITransaction;
import io.sentry.NoOpHub;
import io.sentry.Sentry;
import io.sentry.SentryLevel;
import io.sentry.SentryTraceHeader;
import io.sentry.SpanStatus;
import io.sentry.TransactionContext;
import io.sentry.TransactionOptions;
import io.sentry.exception.InvalidSentryTraceHeaderException;
import io.sentry.protocol.TransactionNameSource;
import io.sentry.util.Objects;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/** Manages {@link io.sentry.Scope} in Webflux request processing. */
@ApiStatus.Experimental
public final class SentryWebFilter implements WebFilter {
  public static final String SENTRY_HUB_KEY = "sentry-hub";
  private static final String TRANSACTION_OP = "http.server";

  private final @NotNull SentryRequestResolver sentryRequestResolver;

  public SentryWebFilter(final @NotNull IHub hub) {
    Objects.requireNonNull(hub, "hub is required");
    this.sentryRequestResolver = new SentryRequestResolver(hub);
  }

  @Override
  public Mono<Void> filter(
      final @NotNull ServerWebExchange serverWebExchange,
      final @NotNull WebFilterChain webFilterChain) {
    @NotNull IHub requestHub = Sentry.cloneMainHub();
    final boolean isTracingEnabled = requestHub.getOptions().isTracingEnabled();
    final @NotNull ServerHttpRequest request = serverWebExchange.getRequest();
    final @Nullable ITransaction transaction =
        requestHub.isEnabled() && isTracingEnabled && shouldTraceRequest(requestHub, request)
            ? startTransaction(requestHub, request)
            : null;

    return webFilterChain
        .filter(serverWebExchange)
        .doFinally(
            __ -> {
              if (transaction != null) {
                finishTransaction(serverWebExchange, transaction);
              }
              requestHub.popScope();
              Sentry.setCurrentHub(NoOpHub.getInstance());
            })
        .doOnError(
            e -> {
              if (transaction != null) {
                transaction.setStatus(SpanStatus.INTERNAL_ERROR);
                transaction.setThrowable(e);
              }
            })
        .doFirst(
            () -> {
              serverWebExchange.getAttributes().put(SENTRY_HUB_KEY, requestHub);
              Sentry.setCurrentHub(requestHub);
              requestHub.pushScope();
              final ServerHttpResponse response = serverWebExchange.getResponse();

              final Hint hint = new Hint();
              hint.set(WEBFLUX_FILTER_REQUEST, request);
              hint.set(WEBFLUX_FILTER_RESPONSE, response);
              final String methodName =
                  request.getMethod() != null ? request.getMethod().name() : "unknown";
              requestHub.addBreadcrumb(
                  Breadcrumb.http(request.getURI().toString(), methodName), hint);
              requestHub.configureScope(
                  scope -> scope.setRequest(sentryRequestResolver.resolveSentryRequest(request)));
            });
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

  private void finishTransaction(ServerWebExchange exchange, ITransaction transaction) {
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
  }
}
