package io.sentry.spring.jakarta.webflux;

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
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;

/** Manages {@link io.sentry.Scope} in Webflux request processing. */
@ApiStatus.Experimental
public abstract class AbstractSentryWebFilter implements WebFilter {
  private final @NotNull SentryRequestResolver sentryRequestResolver;
  public static final String SENTRY_HUB_KEY = "sentry-hub";
  private static final String TRANSACTION_OP = "http.server";

  public AbstractSentryWebFilter(final @NotNull IHub hub) {
    Objects.requireNonNull(hub, "hub is required");
    this.sentryRequestResolver = new SentryRequestResolver(hub);
  }

  protected @Nullable ITransaction maybeStartTransaction(
      final @NotNull IHub requestHub, final @NotNull ServerHttpRequest request) {
    if (requestHub.isEnabled()
        && requestHub.getOptions().isTracingEnabled()
        && shouldTraceRequest(requestHub, request)) {
      return startTransaction(requestHub, request);
    } else {
      return null;
    }
  }

  protected void doFinally(
      final @NotNull ServerWebExchange serverWebExchange,
      final @NotNull IHub requestHub,
      final @Nullable ITransaction transaction) {
    if (transaction != null) {
      finishTransaction(serverWebExchange, transaction);
    }
    if (requestHub.isEnabled()) {
      requestHub.popScope();
    }
    Sentry.setCurrentHub(NoOpHub.getInstance());
  }

  protected void doFirst(
      final @NotNull ServerWebExchange serverWebExchange, final @NotNull IHub requestHub) {
    if (requestHub.isEnabled()) {
      serverWebExchange.getAttributes().put(SENTRY_HUB_KEY, requestHub);
      requestHub.pushScope();
      final ServerHttpRequest request = serverWebExchange.getRequest();
      final ServerHttpResponse response = serverWebExchange.getResponse();

      final Hint hint = new Hint();
      hint.set(WEBFLUX_FILTER_REQUEST, request);
      hint.set(WEBFLUX_FILTER_RESPONSE, response);
      final String methodName =
          request.getMethod() != null ? request.getMethod().name() : "unknown";
      requestHub.addBreadcrumb(Breadcrumb.http(request.getURI().toString(), methodName), hint);
      requestHub.configureScope(
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
      final @NotNull IHub hub, final @NotNull ServerHttpRequest request) {
    return hub.getOptions().isTraceOptionsRequests()
        || !HttpMethod.OPTIONS.equals(request.getMethod());
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
        final @Nullable HttpStatusCode statusCode = response.getStatusCode();
        if (statusCode != null) {
          transaction.setStatus(SpanStatus.fromHttpStatusCode(statusCode.value()));
        }
      }
    }
    transaction.finish();
  }

  protected @NotNull ITransaction startTransaction(
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
