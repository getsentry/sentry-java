package io.sentry.spring;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.Breadcrumb;
import io.sentry.HubAdapter;
import io.sentry.IHub;
import io.sentry.util.Objects;
import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.filter.OncePerRequestFilter;

@Open
public class SentrySpringFilter extends OncePerRequestFilter {
  private final @NotNull IHub hub;
  private final @NotNull SentryRequestResolver requestResolver;

  public SentrySpringFilter(@NotNull IHub hub, @NotNull SentryRequestResolver requestResolver) {
    this.hub = Objects.requireNonNull(hub, "hub is required");
    this.requestResolver = Objects.requireNonNull(requestResolver, "requestResolver is required");
  }

  public SentrySpringFilter(final @NotNull IHub hub) {
    this(hub, new SentryRequestResolver(hub));
  }

  public SentrySpringFilter() {
    this(HubAdapter.getInstance());
  }

  @Override
  protected void doFilterInternal(
      final @NotNull HttpServletRequest servletRequest,
      final @NotNull HttpServletResponse response,
      final @NotNull FilterChain filterChain)
      throws ServletException, IOException {
    final HttpServletRequest request = resolveHttpServletRequest(servletRequest);
    try {
      hub.pushScope();
      hub.addBreadcrumb(Breadcrumb.http(request.getRequestURI(), request.getMethod()));
      hub.configureScope(
          scope -> {
            scope.setRequest(requestResolver.resolveSentryRequest(request));
            scope.addEventProcessor(new SentryRequestHttpServletRequestProcessor(request));
          });
    } finally {
      filterChain.doFilter(request, response);
      hub.popScope();
    }
  }

  private HttpServletRequest resolveHttpServletRequest(HttpServletRequest request) {
    try {
      return new CachedBodyHttpServletRequest(request);
    } catch (IOException e) {
      return request;
    }
  }
}
