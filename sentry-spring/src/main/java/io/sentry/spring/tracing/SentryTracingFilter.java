package io.sentry.spring.tracing;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.IHub;
import io.sentry.InvalidSentryTraceHeaderException;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.SentryTraceHeader;
import io.sentry.SpanContext;
import io.sentry.SpanStatus;
import io.sentry.spring.SentryRequestResolver;
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
 * Creates {@link io.sentry.SentryTransaction} around HTTP request executions.
 *
 * <p>Only requests that have {@link HandlerMapping#BEST_MATCHING_PATTERN_ATTRIBUTE} request
 * attribute set are turned into transactions. This attribute is set in {@link
 * RequestMappingInfoHandlerMapping} on request that have not been dropped with any {@link
 * javax.servlet.Filter}.
 */
@Open
public class SentryTracingFilter extends OncePerRequestFilter {
  /** Operation used by {@link SentryTransaction} created in {@link SentryTracingFilter}. */
  private static final String TRANSACTION_OP = "http";

  private final @NotNull IHub hub;
  private final @NotNull SentryOptions options;
  private final @NotNull SentryRequestResolver requestResolver;

  public SentryTracingFilter(
      final @NotNull IHub hub,
      final @NotNull SentryOptions options,
      final @NotNull SentryRequestResolver requestResolver) {
    this.hub = Objects.requireNonNull(hub, "hub is required");
    this.options = Objects.requireNonNull(options, "options is required");
    this.requestResolver = Objects.requireNonNull(requestResolver, "requestResolver is required");
  }

  @Override
  protected void doFilterInternal(
      final @NotNull HttpServletRequest httpRequest,
      final @NotNull HttpServletResponse httpResponse,
      final @NotNull FilterChain filterChain)
      throws ServletException, IOException {

    final String sentryTraceHeader = httpRequest.getHeader(SentryTraceHeader.SENTRY_TRACE_HEADER);

    final io.sentry.SentryTransaction transaction =
        startTransaction(
            httpRequest.getMethod() + " " + httpRequest.getRequestURI(), sentryTraceHeader);
    try {
      filterChain.doFilter(httpRequest, httpResponse);
    } finally {
      final String pattern =
          (String) httpRequest.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
      if (pattern != null) {
        transaction.setName(httpRequest.getMethod() + " " + pattern);
        transaction.setOp(TRANSACTION_OP);
        transaction.setRequest(requestResolver.resolveSentryRequest(httpRequest));
        transaction.setStatus(SpanStatus.fromHttpStatusCode(httpResponse.getStatus()));
        transaction.finish();
      }
    }
  }

  private io.sentry.SentryTransaction startTransaction(
      final @NotNull String name, final @Nullable String sentryTraceHeader) {
    if (sentryTraceHeader != null) {
      try {
        final SpanContext contexts =
            SpanContext.fromSentryTrace(new SentryTraceHeader(sentryTraceHeader));
        return hub.startTransaction(name, contexts);
      } catch (InvalidSentryTraceHeaderException e) {
        options
            .getLogger()
            .log(SentryLevel.DEBUG, "Failed to parse Sentry trace header: %s", e.getMessage());
      }
    }
    return hub.startTransaction(name);
  }
}
