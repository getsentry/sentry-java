package io.sentry.spring;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.Breadcrumb;
import io.sentry.IHub;
import io.sentry.SentryOptions;
import io.sentry.util.Objects;
import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;
import javax.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.Ordered;

/** Pushes new {@link io.sentry.Scope} on each incoming HTTP request. */
@Open
public class SentrySpringRequestListener implements ServletRequestListener, Ordered {
  private final @NotNull IHub hub;
  private final @NotNull SentryOptions options;

  public SentrySpringRequestListener(
      final @NotNull IHub hub, final @NotNull SentryOptions options) {
    this.hub = Objects.requireNonNull(hub, "hub is required");
    this.options = Objects.requireNonNull(options, "options are required");
  }

  @Override
  public void requestDestroyed(ServletRequestEvent sre) {
    hub.popScope();
  }

  @Override
  public void requestInitialized(ServletRequestEvent sre) {
    hub.pushScope();

    final ServletRequest servletRequest = sre.getServletRequest();
    if (servletRequest instanceof HttpServletRequest) {
      final HttpServletRequest request = (HttpServletRequest) sre.getServletRequest();
      hub.addBreadcrumb(Breadcrumb.http(request.getRequestURI(), request.getMethod()));

      hub.configureScope(
          scope -> {
            scope.addEventProcessor(new SentryRequestHttpServletRequestProcessor(request, options));
          });
    }
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }
}
