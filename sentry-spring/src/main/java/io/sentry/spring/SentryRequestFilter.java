package io.sentry.spring;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.Breadcrumb;
import io.sentry.IHub;
import io.sentry.SentryOptions;
import io.sentry.util.Objects;
import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

/** Pushes new {@link io.sentry.Scope} on each incoming HTTP request. */
@Open
public class SentryRequestFilter extends OncePerRequestFilter implements Ordered {
  private final @NotNull IHub hub;
  private final @NotNull SentryOptions options;

  public SentryRequestFilter(final @NotNull IHub hub, final @NotNull SentryOptions options) {
    this.hub = Objects.requireNonNull(hub, "hub is required");
    this.options = Objects.requireNonNull(options, "options are required");
  }

  @Override
  protected void doFilterInternal(
      final @NotNull HttpServletRequest request,
      final @NotNull HttpServletResponse response,
      final @NotNull FilterChain filterChain)
      throws ServletException, IOException {
    hub.pushScope();
    hub.addBreadcrumb(Breadcrumb.http(request.getRequestURI(), request.getMethod()));

    hub.configureScope(
        scope -> {
          scope.addEventProcessor(new SentryRequestHttpServletRequestProcessor(request, options));
        });

    try {
      filterChain.doFilter(request, response);
    } finally {
      hub.popScope();
    }
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }
}
