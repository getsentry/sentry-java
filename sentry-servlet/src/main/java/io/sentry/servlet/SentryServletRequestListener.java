package io.sentry.servlet;

import static io.sentry.TypeCheckHint.SERVLET_REQUEST;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.Breadcrumb;
import io.sentry.Hint;
import io.sentry.IScopes;
import io.sentry.ISentryLifecycleToken;
import io.sentry.ScopesAdapter;
import io.sentry.util.LifecycleHelper;
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

  public static final String SENTRY_SCOPE_LIFECYCLE_TOKEN_KEY = "sentry-scope-lifecycle";

  private final IScopes scopes;

  public SentryServletRequestListener(@NotNull IScopes scopes) {
    this.scopes = Objects.requireNonNull(scopes, "scopes are required");
  }

  public SentryServletRequestListener() {
    this(ScopesAdapter.getInstance());
  }

  @Override
  public void requestDestroyed(@NotNull ServletRequestEvent servletRequestEvent) {
    final ServletRequest servletRequest = servletRequestEvent.getServletRequest();
    LifecycleHelper.close(servletRequest.getAttribute(SENTRY_SCOPE_LIFECYCLE_TOKEN_KEY));
  }

  @Override
  public void requestInitialized(@NotNull ServletRequestEvent servletRequestEvent) {
    final @NotNull ISentryLifecycleToken lifecycleToken =
        scopes.forkedScopes("SentryServletRequestListener").makeCurrent();

    final ServletRequest servletRequest = servletRequestEvent.getServletRequest();
    servletRequest.setAttribute(SENTRY_SCOPE_LIFECYCLE_TOKEN_KEY, lifecycleToken);
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
