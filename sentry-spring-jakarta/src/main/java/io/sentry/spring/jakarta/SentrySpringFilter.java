package io.sentry.spring.jakarta;

import static io.sentry.SentryOptions.RequestSize.*;
import static io.sentry.TypeCheckHint.SPRING_REQUEST_FILTER_REQUEST;
import static io.sentry.TypeCheckHint.SPRING_REQUEST_FILTER_RESPONSE;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.Breadcrumb;
import io.sentry.EventProcessor;
import io.sentry.Hint;
import io.sentry.HubAdapter;
import io.sentry.IHub;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.SentryOptions.RequestSize;
import io.sentry.spring.jakarta.tracing.SpringMvcTransactionNameProvider;
import io.sentry.spring.jakarta.tracing.TransactionNameProvider;
import io.sentry.util.Objects;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.MediaType;
import org.springframework.util.MimeType;
import org.springframework.web.filter.OncePerRequestFilter;

@Open
public class SentrySpringFilter extends OncePerRequestFilter {
  private final @NotNull IHub hub;
  private final @NotNull SentryRequestResolver requestResolver;
  private final @NotNull TransactionNameProvider transactionNameProvider;

  public SentrySpringFilter(
      final @NotNull IHub hub,
      final @NotNull SentryRequestResolver requestResolver,
      final @NotNull TransactionNameProvider transactionNameProvider) {
    this.hub = Objects.requireNonNull(hub, "hub is required");
    this.requestResolver = Objects.requireNonNull(requestResolver, "requestResolver is required");
    this.transactionNameProvider =
        Objects.requireNonNull(transactionNameProvider, "transactionNameProvider is required");
  }

  public SentrySpringFilter(final @NotNull IHub hub) {
    this(hub, new SentryRequestResolver(hub), new SpringMvcTransactionNameProvider());
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
      // request may qualify for caching request body, if so resolve cached request
      final HttpServletRequest request = resolveHttpServletRequest(servletRequest);
      hub.pushScope();
      try {
        final Hint hint = new Hint();
        hint.set(SPRING_REQUEST_FILTER_REQUEST, servletRequest);
        hint.set(SPRING_REQUEST_FILTER_RESPONSE, response);

        hub.addBreadcrumb(Breadcrumb.http(request.getRequestURI(), request.getMethod()), hint);
        configureScope(request);
        filterChain.doFilter(request, response);
      } finally {
        hub.popScope();
      }
    } else {
      filterChain.doFilter(servletRequest, response);
    }
  }

  private void configureScope(HttpServletRequest request) {
    try {
      hub.configureScope(
          scope -> {
            // set basic request information on the scope
            scope.setRequest(requestResolver.resolveSentryRequest(request));
            // transaction name is known at the later stage of request processing, thus it cannot
            // be set on the scope
            scope.addEventProcessor(
                new SentryRequestHttpServletRequestProcessor(transactionNameProvider, request));
            // only if request caches body, add an event processor that sets body on the event
            // body is not on the scope, to avoid using memory when no event is triggered during
            // request processing
            if (request instanceof CachedBodyHttpServletRequest) {
              scope.addEventProcessor(
                  new RequestBodyExtractingEventProcessor(request, hub.getOptions()));
            }
          });
    } catch (Throwable e) {
      hub.getOptions()
          .getLogger()
          .log(SentryLevel.ERROR, "Failed to set scope for HTTP request", e);
    }
  }

  private @NotNull HttpServletRequest resolveHttpServletRequest(
      final @NotNull HttpServletRequest request) {
    if (hub.getOptions().isSendDefaultPii()
        && qualifiesForCaching(request, hub.getOptions().getMaxRequestBodySize())) {
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

  static final class RequestBodyExtractingEventProcessor implements EventProcessor {
    private final @NotNull RequestPayloadExtractor requestPayloadExtractor =
        new RequestPayloadExtractor();
    private final @NotNull HttpServletRequest request;
    private final @NotNull SentryOptions options;

    public RequestBodyExtractingEventProcessor(
        final @NotNull HttpServletRequest request, final @NotNull SentryOptions options) {
      this.request = Objects.requireNonNull(request, "request is required");
      this.options = Objects.requireNonNull(options, "options is required");
    }

    @Override
    public @NotNull SentryEvent process(@NotNull SentryEvent event, @NotNull Hint hint) {
      if (event.getRequest() != null) {
        event.getRequest().setData(requestPayloadExtractor.extract(request, options));
      }
      return event;
    }
  }
}
