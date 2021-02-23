package io.sentry.spring.tracing;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.CustomSamplingContext;
import io.sentry.HubAdapter;
import io.sentry.IHub;
import io.sentry.ISpan;
import io.sentry.ITransaction;
import io.sentry.SentryLevel;
import io.sentry.SentryTraceHeader;
import io.sentry.SpanStatus;
import io.sentry.TransactionContext;
import io.sentry.exception.InvalidSentryTraceHeaderException;
import io.sentry.util.Objects;
import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping;

/**
 * Creates {@link ITransaction} around HTTP request executions.
 *
 * <p>Only requests that have {@link HandlerMapping#BEST_MATCHING_PATTERN_ATTRIBUTE} request
 * attribute set are turned into transactions. This attribute is set in {@link
 * RequestMappingInfoHandlerMapping} on request that have not been dropped with any {@link
 * javax.servlet.Filter}.
 */
@Open
public class SentryTracingFilter extends OncePerRequestFilter {
  /** Operation used by {@link SentryTransaction} created in {@link SentryTracingFilter}. */
  private static final String TRANSACTION_OP = "http.server";

  private final @NotNull TransactionNameProvider transactionNameProvider;
  private final @NotNull IHub hub;

  public SentryTracingFilter() {
    this(HubAdapter.getInstance());
  }

  public SentryTracingFilter(final @NotNull IHub hub) {
    this(hub, new TransactionNameProvider());
  }

  public SentryTracingFilter(
      final @NotNull IHub hub, final @NotNull TransactionNameProvider transactionNameProvider) {
    this.hub = Objects.requireNonNull(hub, "hub is required");
    this.transactionNameProvider =
        Objects.requireNonNull(transactionNameProvider, "transactionNameProvider is required");
  }

  @Override
  protected void doFilterInternal(
      final @NotNull HttpServletRequest httpRequest,
      final @NotNull HttpServletResponse httpResponse,
      final @NotNull FilterChain filterChain)
      throws ServletException, IOException {

    if (hub.isEnabled()) {
      final String sentryTraceHeader = httpRequest.getHeader(SentryTraceHeader.SENTRY_TRACE_HEADER);

      // at this stage we are not able to get real transaction name
      final ISpan transaction = startTransaction(httpRequest, sentryTraceHeader);
      try {
        filterChain.doFilter(httpRequest, httpResponse);
      } finally {
        // after all filters run, templated path pattern is available in request attribute
        final String transactionName = transactionNameProvider.provideTransactionName(httpRequest);
        // if transaction name is not resolved, the request has not been processed by a controller
        // and
        // we should not report it to Sentry
        if (transactionName != null) {
          transaction.setTag("sentry-name", transactionName);
          transaction.setOperation(TRANSACTION_OP);
          // TODO: should be set in the client
          // transaction.setRequest(requestResolver.resolveSentryRequest(httpRequest));
          transaction.setStatus(SpanStatus.fromHttpStatusCode(httpResponse.getStatus()));
          transaction.finish();
        }
      }
    } else {
      filterChain.doFilter(httpRequest, httpResponse);
    }
  }

  private ISpan startTransaction(
      final @NotNull HttpServletRequest request, final @Nullable String sentryTraceHeader) {

    final String name = request.getMethod() + " " + request.getRequestURI();

    final CustomSamplingContext customSamplingContext = new CustomSamplingContext();
    customSamplingContext.set("request", request);

    if (sentryTraceHeader != null) {
      try {
        final TransactionContext contexts =
            TransactionContext.fromSentryTrace(
                name, "http.server", new SentryTraceHeader(sentryTraceHeader));
        final ISpan transaction = hub.startTransaction(contexts, customSamplingContext);
        hub.configureScope(scope -> scope.setTransaction(transaction));
        return transaction;
      } catch (InvalidSentryTraceHeaderException e) {
        hub.getOptions()
            .getLogger()
            .log(SentryLevel.DEBUG, "Failed to parse Sentry trace header: %s", e.getMessage());
      }
    }
    final ISpan transaction = hub.startTransaction(name, "http.server", customSamplingContext);
    hub.configureScope(scope -> scope.setTransaction(transaction));
    return transaction;
  }
}
