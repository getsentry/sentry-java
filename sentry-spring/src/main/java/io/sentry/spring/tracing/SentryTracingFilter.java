package io.sentry.spring.tracing;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.BaggageHeader;
import io.sentry.CustomSamplingContext;
import io.sentry.HubAdapter;
import io.sentry.IHub;
import io.sentry.ITransaction;
import io.sentry.SentryTraceHeader;
import io.sentry.SpanStatus;
import io.sentry.TransactionContext;
import io.sentry.TransactionOptions;
import io.sentry.protocol.TransactionNameSource;
import io.sentry.util.Objects;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping;

/** Creates {@link ITransaction} around HTTP request executions. */
@Open
public class SentryTracingFilter extends OncePerRequestFilter {
  /** Operation used by {@link SentryTransaction} created in {@link SentryTracingFilter}. */
  private static final String TRANSACTION_OP = "http.server";

  private static final String TRACE_ORIGIN = "auto.http.spring.webmvc";
  private static final String TRANSACTION_ATTR = SentryTracingFilter.class.getName() + ".transaction";

  private final @NotNull TransactionNameProvider transactionNameProvider;
  private final @NotNull IHub hub;

  /**
   * Creates filter that resolves transaction name using {@link SpringMvcTransactionNameProvider}.
   *
   * <p>Only requests that have {@link HandlerMapping#BEST_MATCHING_PATTERN_ATTRIBUTE} request
   * attribute set are turned into transactions. This attribute is set in {@link
   * RequestMappingInfoHandlerMapping} on request that have not been dropped with any {@link
   * javax.servlet.Filter}.
   */
  public SentryTracingFilter() {
    this(HubAdapter.getInstance());
  }

  /**
   * Creates filter that resolves transaction name using transaction name provider given by
   * parameter.
   *
   * @param hub - the hub
   * @param transactionNameProvider - transaction name provider.
   */
  public SentryTracingFilter(
      final @NotNull IHub hub, final @NotNull TransactionNameProvider transactionNameProvider) {
    this.hub = Objects.requireNonNull(hub, "hub is required");
    this.transactionNameProvider =
        Objects.requireNonNull(transactionNameProvider, "transactionNameProvider is required");
  }

  public SentryTracingFilter(final @NotNull IHub hub) {
    this(hub, new SpringMvcTransactionNameProvider());
  }

  @Override
  protected boolean shouldNotFilterAsyncDispatch() {
    return false;
  }

  @Override
  protected void doFilterInternal(
      final @NotNull HttpServletRequest httpRequest,
      final @NotNull HttpServletResponse httpResponse,
      final @NotNull FilterChain filterChain)
      throws ServletException, IOException {

    if (hub.isEnabled()) {
      if (hub.getOptions().isTracingEnabled() && shouldTraceRequest(httpRequest)) {
        doFilterWithTransaction(httpRequest, httpResponse, filterChain);
      } else {
        filterChain.doFilter(httpRequest, httpResponse);
      }
    } else {
      filterChain.doFilter(httpRequest, httpResponse);
    }
  }

  private void doFilterWithTransaction(
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse,
      FilterChain filterChain)
      throws IOException, ServletException {
    final ITransaction transaction;
    if (isAsyncDispatch(httpRequest)) {
      transaction = (ITransaction) httpRequest.getAttribute(TRANSACTION_ATTR);
    } else {
      final @Nullable String sentryTraceHeader =
          httpRequest.getHeader(SentryTraceHeader.SENTRY_TRACE_HEADER);
      final @Nullable List<String> baggageHeader =
          Collections.list(httpRequest.getHeaders(BaggageHeader.BAGGAGE_HEADER));
      final @Nullable TransactionContext transactionContext =
          hub.continueTrace(sentryTraceHeader, baggageHeader);

      // at this stage we are not able to get real transaction name
      transaction = startTransaction(httpRequest, transactionContext);
      transaction.getSpanContext().setOrigin(TRACE_ORIGIN);
      httpRequest.setAttribute(TRANSACTION_ATTR, transaction);
    }

    try {
      filterChain.doFilter(httpRequest, httpResponse);
    } catch (Throwable e) {
      // exceptions that are not handled by Spring
      transaction.setStatus(SpanStatus.INTERNAL_ERROR);
      throw e;
    } finally {
      if (!isAsyncStarted(httpRequest)) {
        // after all filters run, templated path pattern is available in request attribute
        final String transactionName = transactionNameProvider.provideTransactionName(httpRequest);
        final TransactionNameSource transactionNameSource =
            transactionNameProvider.provideTransactionSource();
        // if transaction name is not resolved, the request has not been processed by a controller
        // and we should not report it to Sentry
        if (transactionName != null) {
          transaction.setName(transactionName, transactionNameSource);
          transaction.setOperation(TRANSACTION_OP);
          // if exception has been thrown, transaction status is already set to INTERNAL_ERROR, and
          // httpResponse.getStatus() returns 200.
          if (transaction.getStatus() == null) {
            transaction.setStatus(SpanStatus.fromHttpStatusCode(httpResponse.getStatus()));
          }
          transaction.finish();
        }
      }
    }
  }

  private boolean shouldTraceRequest(final @NotNull HttpServletRequest request) {
    return hub.getOptions().isTraceOptionsRequests()
        || !HttpMethod.OPTIONS.name().equals(request.getMethod());
  }

  private ITransaction startTransaction(
      final @NotNull HttpServletRequest request,
      final @Nullable TransactionContext transactionContext) {

    final String name = request.getMethod() + " " + request.getRequestURI();

    final CustomSamplingContext customSamplingContext = new CustomSamplingContext();
    customSamplingContext.set("request", request);

    if (transactionContext != null) {
      transactionContext.setName(name);
      transactionContext.setTransactionNameSource(TransactionNameSource.URL);
      transactionContext.setOperation("http.server");

      final TransactionOptions transactionOptions = new TransactionOptions();
      transactionOptions.setCustomSamplingContext(customSamplingContext);
      transactionOptions.setBindToScope(true);

      return hub.startTransaction(transactionContext, transactionOptions);
    }

    final TransactionOptions transactionOptions = new TransactionOptions();
    transactionOptions.setCustomSamplingContext(customSamplingContext);
    transactionOptions.setBindToScope(true);

    return hub.startTransaction(
        new TransactionContext(name, TransactionNameSource.URL, "http.server"), transactionOptions);
  }
}
