package io.sentry.servlet;

import static io.sentry.TypeCheckHint.SERVLET_REQUEST;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.Breadcrumb;
import io.sentry.HubAdapter;
import io.sentry.IHub;
import io.sentry.hints.Hints;
import io.sentry.util.Objects;
import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;
import javax.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.NotNull;

/**
 * This request listener pushes a new scope into sentry that enriches a Sentry event with the
 * details about the current request upon sending.
 */
@Open
public class SentryServletRequestListener implements ServletRequestListener {

  private final IHub hub;

  public SentryServletRequestListener(@NotNull IHub hub) {
    this.hub = Objects.requireNonNull(hub, "hub is required");
  }

  public SentryServletRequestListener() {
    this(HubAdapter.getInstance());
  }

  @Override
  public void requestDestroyed(@NotNull ServletRequestEvent servletRequestEvent) {
    hub.popScope();
  }

  @Override
  public void requestInitialized(@NotNull ServletRequestEvent servletRequestEvent) {
    hub.pushScope();

    final ServletRequest servletRequest = servletRequestEvent.getServletRequest();
    if (servletRequest instanceof HttpServletRequest) {
      final HttpServletRequest httpRequest = (HttpServletRequest) servletRequest;

      final Hints hints = new Hints();
      hints.set(SERVLET_REQUEST, httpRequest);

      hub.addBreadcrumb(
          Breadcrumb.http(httpRequest.getRequestURI(), httpRequest.getMethod()), hints);

      hub.configureScope(
          scope -> {
            scope.addEventProcessor(new SentryRequestHttpServletRequestProcessor(httpRequest));
          });
    }
  }
}
