package io.sentry.servlet.jakarta;

import static io.sentry.TypeCheckHint.SERVLET_REQUEST;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.Breadcrumb;
import io.sentry.Hint;
import io.sentry.HubAdapter;
import io.sentry.IHub;
import io.sentry.util.Objects;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestEvent;
import jakarta.servlet.ServletRequestListener;
import jakarta.servlet.http.HttpServletRequest;
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

      final Hint hint = new Hint();
      hint.set(SERVLET_REQUEST, httpRequest);

      hub.addBreadcrumb(
          Breadcrumb.http(httpRequest.getRequestURI(), httpRequest.getMethod()), hint);

      hub.configureScope(
          scope -> {
            scope.addEventProcessor(new SentryRequestHttpServletRequestProcessor(httpRequest));
          });
    }
  }
}
