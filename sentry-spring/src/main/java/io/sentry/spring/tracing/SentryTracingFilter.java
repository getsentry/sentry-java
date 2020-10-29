package io.sentry.spring.tracing;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.IHub;
import io.sentry.SpanStatus;
import io.sentry.spring.SentryRequestResolver;
import io.sentry.util.Objects;
import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.Ordered;

/** Creates {@link io.sentry.SentryTransaction} around every HTTP request execution. */
@Open
public class SentryTracingFilter implements Filter, Ordered {
  /** Operation used by {@link SentryTransaction} created in {@link SentryTracingFilter}. */
  private static final String TRANSACTION_OP = "http";

  private final @NotNull IHub hub;
  private final @NotNull SentryRequestResolver requestResolver;

  public SentryTracingFilter(
      final @NotNull IHub hub, final @NotNull SentryRequestResolver requestResolver) {
    this.hub = Objects.requireNonNull(hub, "hub is required");
    this.requestResolver = requestResolver;
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }

  @Override
  public void doFilter(
      final @NotNull ServletRequest request,
      final @NotNull ServletResponse response,
      final @NotNull FilterChain filterChain)
      throws IOException, ServletException {
    if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
      filterChain.doFilter(request, response);
    } else {
      final HttpServletRequest httpRequest = (HttpServletRequest) request;
      final HttpServletResponse httpResponse = (HttpServletResponse) response;

      final io.sentry.SentryTransaction transaction =
          hub.startTransaction(httpRequest.getMethod() + " " + httpRequest.getRequestURI());
      try {
        filterChain.doFilter(request, response);
      } finally {
        transaction.setOp(TRANSACTION_OP);
        transaction.setRequest(requestResolver.resolveSentryRequest(httpRequest));
        transaction.setStatus(SpanStatus.fromHttpStatusCode(httpResponse.getStatus()));
        transaction.finish();
      }
    }
  }
}
