package io.sentry.openfeign;

import static io.sentry.TypeCheckHint.OPEN_FEIGN_REQUEST;
import static io.sentry.TypeCheckHint.OPEN_FEIGN_RESPONSE;

import feign.Client;
import feign.Request;
import feign.Response;
import io.sentry.BaggageHeader;
import io.sentry.Breadcrumb;
import io.sentry.Hint;
import io.sentry.IHub;
import io.sentry.ISpan;
import io.sentry.SentryTraceHeader;
import io.sentry.SpanDataConvention;
import io.sentry.SpanStatus;
import io.sentry.util.Objects;
import io.sentry.util.PropagationTargetsUtils;
import io.sentry.util.UrlUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A Feign client that creates a span around each executed HTTP call. */
public final class SentryFeignClient implements Client {
  private static final String TRACE_ORIGIN = "auto.http.openfeign";
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
      span.getSpanContext().setOrigin(TRACE_ORIGIN);
      final @NotNull UrlUtils.UrlDetails urlDetails = UrlUtils.parse(request.url());
      span.setDescription(request.httpMethod().name() + " " + urlDetails.getUrlOrFallback());
      urlDetails.applyToSpan(span);

      final RequestWrapper requestWrapper = new RequestWrapper(request);

      if (!span.isNoOp()
          && PropagationTargetsUtils.contain(
              hub.getOptions().getTracePropagationTargets(), request.url())) {
        final SentryTraceHeader sentryTraceHeader = span.toSentryTrace();
        final @Nullable Collection<String> requestBaggageHeader =
            request.headers().get(BaggageHeader.BAGGAGE_HEADER);
        final @Nullable BaggageHeader baggageHeader =
            span.toBaggageHeader(
                requestBaggageHeader != null ? new ArrayList<>(requestBaggageHeader) : null);
        requestWrapper.header(sentryTraceHeader.getName(), sentryTraceHeader.getValue());
        if (baggageHeader != null) {
          requestWrapper.removeHeader(BaggageHeader.BAGGAGE_HEADER);
          requestWrapper.header(baggageHeader.getName(), baggageHeader.getValue());
        }
      }

      try {
        response = delegate.execute(requestWrapper.build(), options);
        // handles both success and error responses
        span.setData(SpanDataConvention.HTTP_STATUS_CODE_KEY, response.status());
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

    final Hint hint = new Hint();
    hint.set(OPEN_FEIGN_REQUEST, request);
    if (response != null) {
      hint.set(OPEN_FEIGN_RESPONSE, response);
    }

    hub.addBreadcrumb(breadcrumb, hint);
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

    public void removeHeader(final @NotNull String name) {
      headers.remove(name);
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
