package io.sentry.spring.jakarta.tracing;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.Baggage;
import io.sentry.BaggageHeader;
import io.sentry.CustomSamplingContext;
import io.sentry.HubAdapter;
import io.sentry.IHub;
import io.sentry.ITransaction;
import io.sentry.SentryLevel;
import io.sentry.SentryTraceHeader;
import io.sentry.SpanStatus;
import io.sentry.TransactionContext;
import io.sentry.TransactionOptions;
import io.sentry.exception.InvalidSentryTraceHeaderException;
import io.sentry.protocol.TransactionNameSource;
import io.sentry.util.Objects;
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

/** Creates {@link ITransaction} around HTTP request executions. */
@Open
public class SentryTracingFilter extends OncePerRequestFilter {
  /** Operation used by {@link SentryTransaction} created in {@link SentryTracingFilter}. */
  private static final String TRANSACTION_OP = "http.server";

  private final @NotNull TransactionNameProvider transactionNameProvider;
  private final @NotNull IHub hub;

  /**
   * Creates filter that resolves transaction name using {@link SpringMvcTransactionNameProvider}.
   *
   * <p>Only requests that have {@link HandlerMapping#BEST_MATCHING_PATTERN_ATTRIBUTE} request
   * attribute set are turned into transactions. This attribute is set in {@link
   * RequestMappingInfoHandlerMapping} on request that have not been dropped with any {@link
   * jakarta.servlet.Filter}.
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
  protected void doFilterInternal(
      final @NotNull HttpServletRequest httpRequest,
      final @NotNull HttpServletResponse httpResponse,
      final @NotNull FilterChain filterChain)
      throws ServletException, IOException {

    if (hub.isEnabled() && shouldTraceRequest(httpRequest)) {
      final String sentryTraceHeader = httpRequest.getHeader(SentryTraceHeader.SENTRY_TRACE_HEADER);
      final List<String> baggageHeader =
          Collections.list(httpRequest.getHeaders(BaggageHeader.BAGGAGE_HEADER));

      // at this stage we are not able to get real transaction name
      final ITransaction transaction =
          startTransaction(httpRequest, sentryTraceHeader, baggageHeader);
      try {
        filterChain.doFilter(httpRequest, httpResponse);
      } catch (Throwable e) {
        // exceptions that are not handled by Spring
        transaction.setStatus(SpanStatus.INTERNAL_ERROR);
        throw e;
      } finally {
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
    } else {
      filterChain.doFilter(httpRequest, httpResponse);
    }
  }

  private boolean shouldTraceRequest(final @NotNull HttpServletRequest request) {
    return hub.getOptions().isTraceOptionsRequests()
        || !HttpMethod.OPTIONS.name().equals(request.getMethod());
  }

  private ITransaction startTransaction(
      final @NotNull HttpServletRequest request,
      final @Nullable String sentryTraceHeader,
      final @Nullable List<String> baggageHeader) {

    final String name = request.getMethod() + " " + request.getRequestURI();

    final CustomSamplingContext customSamplingContext = new CustomSamplingContext();
    customSamplingContext.set("request", request);

    final Baggage baggage = Baggage.fromHeader(baggageHeader, hub.getOptions().getLogger());

    if (sentryTraceHeader != null) {
      try {
        final TransactionContext contexts =
            TransactionContext.fromSentryTrace(
                name,
                TransactionNameSource.URL,
                "http.server",
                new SentryTraceHeader(sentryTraceHeader),
                baggage,
                null);

        final TransactionOptions transactionOptions = new TransactionOptions();
        transactionOptions.setCustomSamplingContext(customSamplingContext);
        transactionOptions.setBindToScope(true);

        return hub.startTransaction(contexts, transactionOptions);
      } catch (InvalidSentryTraceHeaderException e) {
        hub.getOptions()
            .getLogger()
            .log(SentryLevel.DEBUG, e, "Failed to parse Sentry trace header: %s", e.getMessage());
      }
    }

    final TransactionOptions transactionOptions = new TransactionOptions();
    transactionOptions.setCustomSamplingContext(customSamplingContext);
    transactionOptions.setBindToScope(true);

    return hub.startTransaction(
        new TransactionContext(name, TransactionNameSource.URL, "http.server"), transactionOptions);
  }
}
