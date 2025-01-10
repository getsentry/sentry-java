package io.sentry.spring.webflux;

import static io.sentry.TypeCheckHint.WEBFLUX_FILTER_REQUEST;
import static io.sentry.TypeCheckHint.WEBFLUX_FILTER_RESPONSE;

import io.sentry.BaggageHeader;
import io.sentry.Breadcrumb;
import io.sentry.CustomSamplingContext;
import io.sentry.Hint;
import io.sentry.IScope;
import io.sentry.IScopes;
import io.sentry.ITransaction;
import io.sentry.NoOpScopes;
import io.sentry.Sentry;
import io.sentry.SentryTraceHeader;
import io.sentry.SpanStatus;
import io.sentry.TransactionContext;
import io.sentry.TransactionOptions;
import io.sentry.protocol.TransactionNameSource;
import io.sentry.util.Objects;
import io.sentry.util.SpanUtils;
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

/** Manages {@link IScope} in Webflux request processing. */
@ApiStatus.Experimental
public final class SentryWebFilter implements WebFilter {
  public static final String SENTRY_SCOPES_KEY = "sentry-scopes";
  /**
   * @deprecated please use {@link SentryWebFilter#SENTRY_SCOPES_KEY} instead.
   */
  @Deprecated public static final String SENTRY_HUB_KEY = SENTRY_SCOPES_KEY;

  private static final String TRANSACTION_OP = "http.server";
  private static final String TRACE_ORIGIN = "auto.spring.webflux";

  private final @NotNull SentryRequestResolver sentryRequestResolver;

  public SentryWebFilter(final @NotNull IScopes scopes) {
    Objects.requireNonNull(scopes, "scopes are required");
    this.sentryRequestResolver = new SentryRequestResolver(scopes);
  }

  @Override
  public Mono<Void> filter(
      final @NotNull ServerWebExchange serverWebExchange,
      final @NotNull WebFilterChain webFilterChain) {
    @NotNull IScopes requestScopes = Sentry.forkedRootScopes("request.webflux");
    if (!requestScopes.isEnabled() || isIgnored(requestScopes)) {
      return webFilterChain.filter(serverWebExchange);
    }

    final boolean isTracingEnabled = requestScopes.getOptions().isTracingEnabled();
    final @NotNull ServerHttpRequest request = serverWebExchange.getRequest();
    final @NotNull HttpHeaders headers = request.getHeaders();
    final @Nullable String sentryTraceHeader =
        headers.getFirst(SentryTraceHeader.SENTRY_TRACE_HEADER);
    final @Nullable List<String> baggageHeaders = headers.get(BaggageHeader.BAGGAGE_HEADER);
    final @Nullable TransactionContext transactionContext =
        requestScopes.continueTrace(sentryTraceHeader, baggageHeaders);

    final @Nullable ITransaction transaction =
        isTracingEnabled && shouldTraceRequest(requestScopes, request)
            ? startTransaction(requestScopes, request, transactionContext)
            : null;

    return webFilterChain
        .filter(serverWebExchange)
        .doFinally(
            __ -> {
              if (transaction != null) {
                finishTransaction(serverWebExchange, transaction);
              }
              Sentry.setCurrentScopes(NoOpScopes.getInstance());
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
              serverWebExchange.getAttributes().put(SENTRY_SCOPES_KEY, requestScopes);
              Sentry.setCurrentScopes(requestScopes);
              final ServerHttpResponse response = serverWebExchange.getResponse();

              final Hint hint = new Hint();
              hint.set(WEBFLUX_FILTER_REQUEST, request);
              hint.set(WEBFLUX_FILTER_RESPONSE, response);
              final String methodName =
                  request.getMethod() != null ? request.getMethod().name() : "unknown";
              requestScopes.addBreadcrumb(
                  Breadcrumb.http(request.getURI().toString(), methodName), hint);
              requestScopes.configureScope(
                  scope -> scope.setRequest(sentryRequestResolver.resolveSentryRequest(request)));
            });
  }

  private boolean isIgnored(final @NotNull IScopes scopes) {
    return SpanUtils.isIgnored(scopes.getOptions().getIgnoredSpanOrigins(), TRACE_ORIGIN);
  }

  private boolean shouldTraceRequest(
      final @NotNull IScopes scopes, final @NotNull ServerHttpRequest request) {
    return scopes.getOptions().isTraceOptionsRequests()
        || !HttpMethod.OPTIONS.equals(request.getMethod());
  }

  private @NotNull ITransaction startTransaction(
      final @NotNull IScopes scopes,
      final @NotNull ServerHttpRequest request,
      final @Nullable TransactionContext transactionContext) {
    final @NotNull String name = request.getMethod() + " " + request.getURI().getPath();
    final @NotNull CustomSamplingContext customSamplingContext = new CustomSamplingContext();
    customSamplingContext.set("request", request);

    final TransactionOptions transactionOptions = new TransactionOptions();
    transactionOptions.setCustomSamplingContext(customSamplingContext);
    transactionOptions.setBindToScope(true);
    transactionOptions.setOrigin(TRACE_ORIGIN);

    if (transactionContext != null) {
      transactionContext.setName(name);
      transactionContext.setTransactionNameSource(TransactionNameSource.URL);
      transactionContext.setOperation(TRANSACTION_OP);

      return scopes.startTransaction(transactionContext, transactionOptions);
    }

    return scopes.startTransaction(
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
        transaction
            .getContexts()
            .withResponse(
                (sentryResponse) -> {
                  sentryResponse.setStatusCode(rawStatusCode);
                });
        if (transaction.getStatus() == null) {
          transaction.setStatus(SpanStatus.fromHttpStatusCode(rawStatusCode));
        }
      }
    }
    transaction.finish();
  }
}
