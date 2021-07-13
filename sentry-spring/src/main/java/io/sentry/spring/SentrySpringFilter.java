package io.sentry.spring;

import static io.sentry.SentryOptions.RequestSize.*;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.Breadcrumb;
import io.sentry.HubAdapter;
import io.sentry.IHub;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions.RequestSize;
import io.sentry.util.Objects;
import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.MediaType;
import org.springframework.util.MimeType;
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
    if (hub.isEnabled()) {
      final HttpServletRequest request = resolveHttpServletRequest(servletRequest);
      try {
        hub.pushScope();
        hub.addBreadcrumb(Breadcrumb.http(request.getRequestURI(), request.getMethod()));
        hub.configureScope(
            scope -> {
              scope.setRequest(requestResolver.resolveSentryRequest(request));
              scope.addEventProcessor(new SentryRequestHttpServletRequestProcessor(request));
            });
      } catch (Exception e) {
        hub.getOptions()
            .getLogger()
            .log(SentryLevel.ERROR, "Failed to set scope for HTTP request", e);
      } finally {
        filterChain.doFilter(request, response);
        hub.popScope();
      }
    } else {
      filterChain.doFilter(servletRequest, response);
    }
  }

  private @NotNull HttpServletRequest resolveHttpServletRequest(
      final @NotNull HttpServletRequest request) {
    if (qualifiesForCaching(request, hub.getOptions().getMaxRequestBodySize())) {
      try {
        return new CachedBodyHttpServletRequest(request);
      } catch (IOException e) {
        hub.getOptions()
            .getLogger()
            .log(
                SentryLevel.WARNING,
                "Failed to cache HTTP request body. Request body will not be attached to Sentry events.",
                e);
        return request;
      }
    }
    return request;
  }

  private boolean qualifiesForCaching(
      final @NotNull HttpServletRequest request, final @NotNull RequestSize maxRequestBodySize) {
    final int contentLength = request.getContentLength();
    final String contentType = request.getContentType();

    return maxRequestBodySize != RequestSize.NONE
        && contentLength != -1
        && contentType != null
        && MimeType.valueOf(contentType).isCompatibleWith(MediaType.APPLICATION_JSON)
        && ((maxRequestBodySize == SMALL && contentLength < 1000)
            || (maxRequestBodySize == MEDIUM && contentLength < 10000)
            || maxRequestBodySize == ALWAYS);
  }
}
