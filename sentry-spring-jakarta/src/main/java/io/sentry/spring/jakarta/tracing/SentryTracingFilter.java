package io.sentry.spring.jakarta.tracing;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.BaggageHeader;
import io.sentry.CustomSamplingContext;
import io.sentry.IScopes;
import io.sentry.ITransaction;
import io.sentry.ScopesAdapter;
import io.sentry.SentryTraceHeader;
import io.sentry.SpanStatus;
import io.sentry.TransactionContext;
import io.sentry.TransactionOptions;
import io.sentry.protocol.TransactionNameSource;
import io.sentry.util.Objects;
import io.sentry.util.SpanUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping;

/**
 * Creates {@link ITransaction} around HTTP request executions if performance is enabled. Otherwise
 * just reads tracing information from incoming request.
 */
@Open
public class SentryTracingFilter extends OncePerRequestFilter {
  /** Operation used by {@link SentryTransaction} created in {@link SentryTracingFilter}. */
  private static final String TRANSACTION_OP = "http.server";

  private static final String TRACE_ORIGIN = "auto.http.spring_jakarta.webmvc";
  private static final String TRANSACTION_ATTR = "sentry.transaction";

  private final @NotNull TransactionNameProvider transactionNameProvider;
  private final @NotNull IScopes scopes;
  private final boolean isAsyncSupportEnabled;

  /**
   * Creates filter that resolves transaction name using {@link SpringMvcTransactionNameProvider}.
   *
   * <p>Only requests that have {@link HandlerMapping#BEST_MATCHING_PATTERN_ATTRIBUTE} request
   * attribute set are turned into transactions. This attribute is set in {@link
   * RequestMappingInfoHandlerMapping} on request that have not been dropped with any {@link
   * jakarta.servlet.Filter}.
   */
  public SentryTracingFilter() {
    this(ScopesAdapter.getInstance());
  }

  /**
   * Creates filter that resolves transaction name using transaction name provider given by
   * parameter.
   *
   * @param scopes - the scopes
   * @param transactionNameProvider - transaction name provider.
   */
  public SentryTracingFilter(
      final @NotNull IScopes scopes,
      final @NotNull TransactionNameProvider transactionNameProvider) {
    this(scopes, transactionNameProvider, false);
  }

  /**
   * Creates filter that resolves transaction name using transaction name provider given by
   * parameter.
   *
   * @param scopes - the scopes
   * @param transactionNameProvider - transaction name provider.
   * @param isAsyncSupportEnabled - whether transactions should be kept open until async handling is
   *     done
   */
  public SentryTracingFilter(
      final @NotNull IScopes scopes,
      final @NotNull TransactionNameProvider transactionNameProvider,
      final boolean isAsyncSupportEnabled) {
    this.scopes = Objects.requireNonNull(scopes, "Scopes are required");
    this.transactionNameProvider =
        Objects.requireNonNull(transactionNameProvider, "transactionNameProvider is required");
    this.isAsyncSupportEnabled = isAsyncSupportEnabled;
  }

  public SentryTracingFilter(final @NotNull IScopes scopes) {
    this(scopes, new SpringMvcTransactionNameProvider());
  }

  @Override
  protected boolean shouldNotFilterAsyncDispatch() {
    return !isAsyncSupportEnabled;
  }

  @Override
  protected void doFilterInternal(
      final @NotNull HttpServletRequest httpRequest,
      final @NotNull HttpServletResponse httpResponse,
      final @NotNull FilterChain filterChain)
      throws ServletException, IOException {
    if (scopes.isEnabled() && !isIgnored()) {
      @Nullable TransactionContext transactionContext = null;
      if (shouldContinueTrace(httpRequest)) {
        final @Nullable String sentryTraceHeader =
            httpRequest.getHeader(SentryTraceHeader.SENTRY_TRACE_HEADER);
        final @Nullable List<String> baggageHeader =
            Collections.list(httpRequest.getHeaders(BaggageHeader.BAGGAGE_HEADER));
        transactionContext = scopes.continueTrace(sentryTraceHeader, baggageHeader);
      }
      if (scopes.getOptions().isTracingEnabled() && shouldTraceRequest(httpRequest)) {
        doFilterWithTransaction(httpRequest, httpResponse, filterChain, transactionContext);
      } else {
        filterChain.doFilter(httpRequest, httpResponse);
      }
    } else {
      filterChain.doFilter(httpRequest, httpResponse);
    }
  }

  private boolean isIgnored() {
    return SpanUtils.isIgnored(scopes.getOptions().getIgnoredSpanOrigins(), TRACE_ORIGIN);
  }

  private void doFilterWithTransaction(
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse,
      FilterChain filterChain,
      final @Nullable TransactionContext transactionContext)
      throws IOException, ServletException {
    final @Nullable ITransaction transaction =
        getOrStartTransaction(httpRequest, transactionContext);

    try {
      filterChain.doFilter(httpRequest, httpResponse);
    } catch (Throwable e) {
      if (transaction != null) {
        // exceptions that are not handled by Spring
        transaction.setStatus(SpanStatus.INTERNAL_ERROR);
      }
      throw e;
    } finally {
      if (shouldFinishTransaction(httpRequest) && transaction != null) {
        // after all filters run, templated path pattern is available in request attribute
        final @NotNull TransactionNameWithSource transactionNameWithSource =
            transactionNameProvider.provideTransactionNameAndSource(httpRequest);
        final @Nullable String transactionName = transactionNameWithSource.getTransactionName();
        final @NotNull TransactionNameSource transactionNameSource =
            transactionNameWithSource.getTransactionNameSource();
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

  private ITransaction getOrStartTransaction(
      final @NotNull HttpServletRequest httpRequest,
      final @Nullable TransactionContext transactionContext) {
    if (isAsyncDispatch(httpRequest)) {
      // second invocation of this filter for the same async request already has the transaction
      // in the attributes
      return (ITransaction) httpRequest.getAttribute(TRANSACTION_ATTR);
    } else {
      // at this stage we are not able to get real transaction name
      final @NotNull ITransaction transaction = startTransaction(httpRequest, transactionContext);
      if (shouldStoreTransactionForAsyncProcessing()) {
        httpRequest.setAttribute(TRANSACTION_ATTR, transaction);
      }
      return transaction;
    }
  }

  /**
   * Returns false if an async request is being dispatched (second invocation of the filter for the
   * same async request).
   *
   * <p>Returns true if not an async request or this is the first invocation of the filter for the
   * same async request
   */
  private boolean shouldContinueTrace(HttpServletRequest httpRequest) {
    return !isAsyncSupportEnabled || !isAsyncDispatch(httpRequest);
  }

  private boolean shouldStoreTransactionForAsyncProcessing() {
    return isAsyncSupportEnabled;
  }

  /**
   * Returns false if async request handling has only been started but not yet finished (first
   * invocation of this filter for the same async request).
   *
   * <p>Returns true if not an async request or async request handling has finished (second
   * invocation of this filter for the same async request)
   *
   * <p>Note: isAsyncStarted changes its return value after filterChain.doFilter() of the first
   * async invocation
   */
  private boolean shouldFinishTransaction(HttpServletRequest httpRequest) {
    return !isAsyncSupportEnabled || !isAsyncStarted(httpRequest);
  }

  private boolean shouldTraceRequest(final @NotNull HttpServletRequest request) {
    return scopes.getOptions().isTraceOptionsRequests()
        || !HttpMethod.OPTIONS.name().equals(request.getMethod());
  }

  private ITransaction startTransaction(
      final @NotNull HttpServletRequest request,
      final @Nullable TransactionContext transactionContext) {

    final String name = request.getMethod() + " " + request.getRequestURI();

    final CustomSamplingContext customSamplingContext = new CustomSamplingContext();
    customSamplingContext.set("request", request);

    final TransactionOptions transactionOptions = new TransactionOptions();
    transactionOptions.setCustomSamplingContext(customSamplingContext);
    transactionOptions.setBindToScope(true);
    transactionOptions.setOrigin(TRACE_ORIGIN);

    if (transactionContext != null) {
      transactionContext.setName(name);
      transactionContext.setTransactionNameSource(TransactionNameSource.URL);
      transactionContext.setOperation("http.server");

      return scopes.startTransaction(transactionContext, transactionOptions);
    }

    return scopes.startTransaction(
        new TransactionContext(name, TransactionNameSource.URL, "http.server"), transactionOptions);
  }
}
