package io.sentry.openfeign;

import static io.sentry.TypeCheckHint.OPEN_FEIGN_REQUEST;
import static io.sentry.TypeCheckHint.OPEN_FEIGN_RESPONSE;

import feign.Client;
import feign.Request;
import feign.Response;
import io.sentry.Breadcrumb;
import io.sentry.IHub;
import io.sentry.ISpan;
import io.sentry.SentryTraceHeader;
import io.sentry.SpanStatus;
import io.sentry.util.Objects;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A Feign client that creates a span around each executed HTTP call. */
public final class SentryFeignClient implements Client {
  private final @NotNull Client delegate;
  private final @NotNull IHub hub;
  private final @Nullable BeforeSpanCallback beforeSpan;

  public SentryFeignClient(
      final @NotNull Client delegate,
      final @NotNull IHub hub,
      final @Nullable BeforeSpanCallback beforeSpan) {
    this.delegate = Objects.requireNonNull(delegate, "delegate is required");
    this.hub = Objects.requireNonNull(hub, "hub is required");
    this.beforeSpan = beforeSpan;
  }

  @Override
  public Response execute(final @NotNull Request request, final @NotNull Request.Options options)
      throws IOException {
    Response response = null;
    try {
      final ISpan activeSpan = hub.getSpan();
      if (activeSpan == null) {
        return delegate.execute(request, options);
      }

      ISpan span = activeSpan.startChild("http.client");
      span.setDescription(request.httpMethod().name() + " " + request.url());

      final SentryTraceHeader sentryTraceHeader = span.toSentryTrace();
      final RequestWrapper requestWrapper = new RequestWrapper(request);
      requestWrapper.header(sentryTraceHeader.getName(), sentryTraceHeader.getValue());
      try {
        response = delegate.execute(requestWrapper.build(), options);
        // handles both success and error responses
        span.setStatus(SpanStatus.fromHttpStatusCode(response.status()));
        return response;
      } catch (Throwable e) {
        // handles cases like connection errors
        span.setThrowable(e);
        span.setStatus(SpanStatus.INTERNAL_ERROR);
        throw e;
      } finally {
        if (beforeSpan != null) {
          final ISpan result = beforeSpan.execute(span, request, response);

          if (result == null) {
            // span is dropped
            span.getSpanContext().setSampled(false);
          } else {
            span.finish();
          }
        } else {
          span.finish();
        }
      }
    } finally {
      addBreadcrumb(request, response);
    }
  }

  private void addBreadcrumb(final @NotNull Request request, final @Nullable Response response) {
    final Breadcrumb breadcrumb =
        Breadcrumb.http(
            request.url(),
            request.httpMethod().name(),
            response != null ? response.status() : null);
    breadcrumb.setData("request_body_size", request.body() != null ? request.body().length : 0);
    if (response != null && response.body() != null && response.body().length() != null) {
      breadcrumb.setData("response_body_size", response.body().length());
    }

    final Map<String, Object> hintMap = new HashMap<>();
    hintMap.put(OPEN_FEIGN_REQUEST, request);
    if (response != null) {
      hintMap.put(OPEN_FEIGN_RESPONSE, response);
    }

    hub.addBreadcrumb(breadcrumb, hintMap);
  }

  static final class RequestWrapper {
    private final @NotNull Request delegate;

    private final @NotNull Map<String, Collection<String>> headers;

    RequestWrapper(final @NotNull Request delegate) {
      this.delegate = delegate;
      this.headers = new LinkedHashMap<>(delegate.headers());
    }

    public void header(final @NotNull String name, final @NotNull String value) {
      if (!headers.containsKey(name)) {
        headers.put(name, Collections.singletonList(value));
      }
    }

    @NotNull
    Request build() {
      return Request.create(
          delegate.httpMethod(),
          delegate.url(),
          headers,
          delegate.body(),
          delegate.charset(),
          delegate.requestTemplate());
    }
  }

  public interface BeforeSpanCallback {
    @Nullable
    ISpan execute(@NotNull ISpan span, @NotNull Request request, @Nullable Response response);
  }
}
