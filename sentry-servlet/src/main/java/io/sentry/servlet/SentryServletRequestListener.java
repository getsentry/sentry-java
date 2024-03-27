package io.sentry.servlet;

import static io.sentry.TypeCheckHint.SERVLET_REQUEST;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.Breadcrumb;
import io.sentry.Hint;
import io.sentry.IScopes;
import io.sentry.ScopesAdapter;
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

  private final IScopes scopes;

  public SentryServletRequestListener(@NotNull IScopes scopes) {
    this.scopes = Objects.requireNonNull(scopes, "scopes are required");
  }

  public SentryServletRequestListener() {
    this(ScopesAdapter.getInstance());
  }

  @Override
  public void requestDestroyed(@NotNull ServletRequestEvent servletRequestEvent) {
    scopes.popScope();
  }

  @Override
  public void requestInitialized(@NotNull ServletRequestEvent servletRequestEvent) {
    scopes.pushScope();

    final ServletRequest servletRequest = servletRequestEvent.getServletRequest();
    if (servletRequest instanceof HttpServletRequest) {
      final HttpServletRequest httpRequest = (HttpServletRequest) servletRequest;

      final Hint hint = new Hint();
      hint.set(SERVLET_REQUEST, httpRequest);

      scopes.addBreadcrumb(
          Breadcrumb.http(httpRequest.getRequestURI(), httpRequest.getMethod()), hint);

      scopes.configureScope(
          scope -> {
            scope.addEventProcessor(new SentryRequestHttpServletRequestProcessor(httpRequest));
          });
    }
  }
}
