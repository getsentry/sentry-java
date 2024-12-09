package io.sentry.spring.jakarta;

import static io.sentry.SentryOptions.RequestSize.*;
import static io.sentry.TypeCheckHint.SPRING_REQUEST_FILTER_REQUEST;
import static io.sentry.TypeCheckHint.SPRING_REQUEST_FILTER_RESPONSE;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.Breadcrumb;
import io.sentry.EventProcessor;
import io.sentry.Hint;
import io.sentry.IScopes;
import io.sentry.ISentryLifecycleToken;
import io.sentry.ScopesAdapter;
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
import org.jetbrains.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.util.MimeType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

@Open
public class SentrySpringFilter extends OncePerRequestFilter {
  private final @NotNull IScopes scopesBeforeForking;
  private final @NotNull SentryRequestResolver requestResolver;
  private final @NotNull TransactionNameProvider transactionNameProvider;

  public SentrySpringFilter(
      final @NotNull IScopes scopes,
      final @NotNull SentryRequestResolver requestResolver,
      final @NotNull TransactionNameProvider transactionNameProvider) {
    this.scopesBeforeForking = Objects.requireNonNull(scopes, "scopes are required");
    this.requestResolver = Objects.requireNonNull(requestResolver, "requestResolver is required");
    this.transactionNameProvider =
        Objects.requireNonNull(transactionNameProvider, "transactionNameProvider is required");
  }

  public SentrySpringFilter(final @NotNull IScopes scopes) {
    this(scopes, new SentryRequestResolver(scopes), new SpringMvcTransactionNameProvider());
  }

  public SentrySpringFilter() {
    this(ScopesAdapter.getInstance());
  }

  @Override
  protected void doFilterInternal(
      final @NotNull HttpServletRequest servletRequest,
      final @NotNull HttpServletResponse response,
      final @NotNull FilterChain filterChain)
      throws ServletException, IOException {
    if (scopesBeforeForking.isEnabled()) {
      // request may qualify for caching request body, if so resolve cached request
      final HttpServletRequest request =
          resolveHttpServletRequest(scopesBeforeForking, servletRequest);
      final @NotNull IScopes forkedScopes = scopesBeforeForking.forkedScopes("SentrySpringFilter");
      try (final @NotNull ISentryLifecycleToken ignored = forkedScopes.makeCurrent()) {
        final Hint hint = new Hint();
        hint.set(SPRING_REQUEST_FILTER_REQUEST, servletRequest);
        hint.set(SPRING_REQUEST_FILTER_RESPONSE, response);

        forkedScopes.addBreadcrumb(
            Breadcrumb.http(request.getRequestURI(), request.getMethod()), hint);
        configureScope(forkedScopes, request);
        filterChain.doFilter(request, response);
      }
    } else {
      filterChain.doFilter(servletRequest, response);
    }
  }

  private void configureScope(
      final @NotNull IScopes scopes, final @NotNull HttpServletRequest request) {
    try {
      scopes.configureScope(
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
            if (request instanceof ContentCachingRequestWrapper) {
              scope.addEventProcessor(
                  new RequestBodyExtractingEventProcessor(request, scopes.getOptions()));
            }
          });
    } catch (Throwable e) {
      scopes
          .getOptions()
          .getLogger()
          .log(SentryLevel.ERROR, "Failed to set scope for HTTP request", e);
    }
  }

  private @NotNull HttpServletRequest resolveHttpServletRequest(
      final @NotNull IScopes scopes, final @NotNull HttpServletRequest request) {
    if (scopes.getOptions().isSendDefaultPii()
        && qualifiesForCaching(request, scopes.getOptions().getMaxRequestBodySize())) {
      return new ContentCachingRequestWrapper(request);
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
        && shouldCacheMimeType(contentType)
        && ((maxRequestBodySize == SMALL && contentLength < 1000)
            || (maxRequestBodySize == MEDIUM && contentLength < 10000)
            || maxRequestBodySize == ALWAYS);
  }

  private static boolean shouldCacheMimeType(String contentType) {
    return MimeType.valueOf(contentType).isCompatibleWith(MediaType.APPLICATION_JSON)
        || MimeType.valueOf(contentType).isCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED);
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

    @Override
    public @Nullable Long getOrder() {
      return 3000L;
    }
  }
}
