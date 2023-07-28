package io.sentry.spring.webflux;

import static io.sentry.TypeCheckHint.WEBFLUX_FILTER_REQUEST;
import static io.sentry.TypeCheckHint.WEBFLUX_FILTER_RESPONSE;

import io.sentry.BaggageHeader;
import io.sentry.Breadcrumb;
import io.sentry.CustomSamplingContext;
import io.sentry.Hint;
import io.sentry.IHub;
import io.sentry.ITransaction;
import io.sentry.NoOpHub;
import io.sentry.Sentry;
import io.sentry.SentryTraceHeader;
import io.sentry.SpanDataConvention;
import io.sentry.SpanStatus;
import io.sentry.TransactionContext;
import io.sentry.TransactionOptions;
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
  private static final String TRACE_ORIGIN = "auto.spring.webflux";

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
    if (!requestHub.isEnabled()) {
      return webFilterChain.filter(serverWebExchange);
    }

    final boolean isTracingEnabled = requestHub.getOptions().isTracingEnabled();
    final @NotNull ServerHttpRequest request = serverWebExchange.getRequest();
    final @NotNull HttpHeaders headers = request.getHeaders();
    final @Nullable String sentryTraceHeader =
        headers.getFirst(SentryTraceHeader.SENTRY_TRACE_HEADER);
    final @Nullable List<String> baggageHeaders = headers.get(BaggageHeader.BAGGAGE_HEADER);
    final @Nullable TransactionContext transactionContext =
        requestHub.continueTrace(sentryTraceHeader, baggageHeaders);

    final @Nullable ITransaction transaction =
        isTracingEnabled && shouldTraceRequest(requestHub, request)
            ? startTransaction(requestHub, request, transactionContext)
            : null;

    if (transaction != null) {
      transaction.getSpanContext().setOrigin(TRACE_ORIGIN);
    }

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
      final @NotNull IHub hub,
      final @NotNull ServerHttpRequest request,
      final @Nullable TransactionContext transactionContext) {
    final @NotNull String name = request.getMethod() + " " + request.getURI().getPath();
    final @NotNull CustomSamplingContext customSamplingContext = new CustomSamplingContext();
    customSamplingContext.set("request", request);

    final TransactionOptions transactionOptions = new TransactionOptions();
    transactionOptions.setCustomSamplingContext(customSamplingContext);
    transactionOptions.setBindToScope(true);

    if (transactionContext != null) {
      transactionContext.setName(name);
      transactionContext.setTransactionNameSource(TransactionNameSource.URL);
      transactionContext.setOperation(TRANSACTION_OP);

      return hub.startTransaction(transactionContext, transactionOptions);
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
    final @Nullable ServerHttpResponse response = exchange.getResponse();
    if (response != null) {
      final @Nullable Integer rawStatusCode = response.getRawStatusCode();
      if (rawStatusCode != null) {
        transaction.setData(SpanDataConvention.HTTP_STATUS_CODE_KEY, rawStatusCode);
        if (transaction.getStatus() == null) {
          transaction.setStatus(SpanStatus.fromHttpStatusCode(rawStatusCode));
        }
      }
    }
    final @Nullable ServerHttpRequest request = exchange.getRequest();
    if (request != null) {
      final @Nullable HttpMethod method = request.getMethod();
      if (method != null) {
        transaction.setData(SpanDataConvention.HTTP_METHOD_KEY, method.name());
      }
    }
    transaction.finish();
  }
}
