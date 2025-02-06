package io.sentry.spring.jakarta.webflux;

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
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;

/** Manages {@link IScope} in Webflux request processing. */
@ApiStatus.Experimental
public abstract class AbstractSentryWebFilter implements WebFilter {
  private final @NotNull SentryRequestResolver sentryRequestResolver;
  public static final String SENTRY_SCOPES_KEY = "sentry-scopes";

  /**
   * @deprecated please use {@link AbstractSentryWebFilter#SENTRY_SCOPES_KEY} instead.
   */
  @Deprecated public static final String SENTRY_HUB_KEY = SENTRY_SCOPES_KEY;

  private static final String TRANSACTION_OP = "http.server";

  public AbstractSentryWebFilter(final @NotNull IScopes scopes) {
    Objects.requireNonNull(scopes, "scopes are required");
    this.sentryRequestResolver = new SentryRequestResolver(scopes);
  }

  protected @Nullable ITransaction maybeStartTransaction(
      final @NotNull IScopes requestScopes,
      final @NotNull ServerHttpRequest request,
      final @NotNull String origin) {
    if (requestScopes.isEnabled() && !isIgnored(requestScopes, origin)) {
      final @NotNull HttpHeaders headers = request.getHeaders();
      final @Nullable String sentryTraceHeader =
          headers.getFirst(SentryTraceHeader.SENTRY_TRACE_HEADER);
      final @Nullable List<String> baggageHeaders = headers.get(BaggageHeader.BAGGAGE_HEADER);
      final @Nullable TransactionContext transactionContext =
          requestScopes.continueTrace(sentryTraceHeader, baggageHeaders);

      if (requestScopes.getOptions().isTracingEnabled()
          && shouldTraceRequest(requestScopes, request)) {
        return startTransaction(requestScopes, request, transactionContext, origin);
      }
    }

    return null;
  }

  private boolean isIgnored(final @NotNull IScopes scopes, final @NotNull String origin) {
    return SpanUtils.isIgnored(scopes.getOptions().getIgnoredSpanOrigins(), origin);
  }

  protected void doFinally(
      final @NotNull ServerWebExchange serverWebExchange,
      final @NotNull IScopes requestScopes,
      final @Nullable ITransaction transaction) {
    if (transaction != null) {
      finishTransaction(serverWebExchange, transaction);
    }
    Sentry.setCurrentScopes(NoOpScopes.getInstance());
  }

  protected void doFirst(
      final @NotNull ServerWebExchange serverWebExchange, final @NotNull IScopes requestScopes) {
    if (requestScopes.isEnabled()) {
      serverWebExchange.getAttributes().put(SENTRY_SCOPES_KEY, requestScopes);
      final ServerHttpRequest request = serverWebExchange.getRequest();
      final ServerHttpResponse response = serverWebExchange.getResponse();

      final Hint hint = new Hint();
      hint.set(WEBFLUX_FILTER_REQUEST, request);
      hint.set(WEBFLUX_FILTER_RESPONSE, response);
      final String methodName =
          request.getMethod() != null ? request.getMethod().name() : "unknown";
      requestScopes.addBreadcrumb(Breadcrumb.http(request.getURI().toString(), methodName), hint);
      requestScopes.configureScope(
          scope -> scope.setRequest(sentryRequestResolver.resolveSentryRequest(request)));
    }
  }

  protected void doOnError(final @Nullable ITransaction transaction, final @NotNull Throwable e) {
    if (transaction != null) {
      transaction.setStatus(SpanStatus.INTERNAL_ERROR);
      transaction.setThrowable(e);
    }
  }

  protected boolean shouldTraceRequest(
      final @NotNull IScopes scopes, final @NotNull ServerHttpRequest request) {
    return scopes.getOptions().isTraceOptionsRequests()
        || !HttpMethod.OPTIONS.equals(request.getMethod());
  }

  private void finishTransaction(ServerWebExchange exchange, ITransaction transaction) {
    String transactionName = TransactionNameProvider.provideTransactionName(exchange);
    if (transactionName != null) {
      transaction.setName(transactionName, TransactionNameSource.ROUTE);
      transaction.setOperation(TRANSACTION_OP);
    }
    final @Nullable ServerHttpResponse response = exchange.getResponse();
    if (response != null) {
      final @Nullable HttpStatusCode statusCode = response.getStatusCode();
      if (statusCode != null) {
        transaction
            .getContexts()
            .withResponse(
                (sentryResponse) -> {
                  sentryResponse.setStatusCode(statusCode.value());
                });
        if (transaction.getStatus() == null) {
          transaction.setStatus(SpanStatus.fromHttpStatusCode(statusCode.value()));
        }
      }
    }
    transaction.finish();
  }

  protected @NotNull ITransaction startTransaction(
      final @NotNull IScopes scopes,
      final @NotNull ServerHttpRequest request,
      final @Nullable TransactionContext transactionContext,
      final @NotNull String origin) {
    final @NotNull String name = request.getMethod() + " " + request.getURI().getPath();
    final @NotNull CustomSamplingContext customSamplingContext = new CustomSamplingContext();
    customSamplingContext.set("request", request);

    final TransactionOptions transactionOptions = new TransactionOptions();
    transactionOptions.setCustomSamplingContext(customSamplingContext);
    transactionOptions.setBindToScope(true);
    transactionOptions.setOrigin(origin);

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
}
