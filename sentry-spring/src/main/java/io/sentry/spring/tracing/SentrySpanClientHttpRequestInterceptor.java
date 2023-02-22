package io.sentry.spring.tracing;

import static io.sentry.TypeCheckHint.SPRING_REQUEST_INTERCEPTOR_REQUEST;
import static io.sentry.TypeCheckHint.SPRING_REQUEST_INTERCEPTOR_REQUEST_BODY;
import static io.sentry.TypeCheckHint.SPRING_REQUEST_INTERCEPTOR_RESPONSE;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.BaggageHeader;
import io.sentry.Breadcrumb;
import io.sentry.Hint;
import io.sentry.IHub;
import io.sentry.ISpan;
import io.sentry.SentryTraceHeader;
import io.sentry.SpanStatus;
import io.sentry.util.Objects;
import io.sentry.util.PropagationTargetsUtils;
import io.sentry.util.UrlUtils;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

@Open
public class SentrySpanClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {
  private final @NotNull IHub hub;

  public SentrySpanClientHttpRequestInterceptor(final @NotNull IHub hub) {
    this.hub = Objects.requireNonNull(hub, "hub is required");
  }

  @Override
  public @NotNull ClientHttpResponse intercept(
      @NotNull HttpRequest request,
      @NotNull byte[] body,
      @NotNull ClientHttpRequestExecution execution)
      throws IOException {
    Integer responseStatusCode = null;
    ClientHttpResponse response = null;
    try {
      final ISpan activeSpan = hub.getSpan();
      if (activeSpan == null) {
        return execution.execute(request, body);
      }

      final ISpan span = activeSpan.startChild("http.client");
      final String methodName =
          request.getMethod() != null ? request.getMethod().name() : "unknown";
      final @NotNull UrlUtils.UrlDetails urlDetails = UrlUtils.parse(request.getURI().toString());
      urlDetails.applyToSpan(span);
      span.setDescription(methodName + " " + urlDetails.getUrlOrFallback());

      if (!span.isNoOp()
          && PropagationTargetsUtils.contain(
              hub.getOptions().getTracePropagationTargets(), request.getURI())) {
        final SentryTraceHeader sentryTraceHeader = span.toSentryTrace();
        request.getHeaders().add(sentryTraceHeader.getName(), sentryTraceHeader.getValue());
        @Nullable
        BaggageHeader baggage =
            span.toBaggageHeader(request.getHeaders().get(BaggageHeader.BAGGAGE_HEADER));
        if (baggage != null) {
          request.getHeaders().set(baggage.getName(), baggage.getValue());
        }
      }

      try {
        response = execution.execute(request, body);
        // handles both success and error responses
        span.setStatus(SpanStatus.fromHttpStatusCode(response.getStatusCode().value()));
        responseStatusCode = response.getStatusCode().value();
        return response;
      } catch (Throwable e) {
        // handles cases like connection errors
        span.setThrowable(e);
        span.setStatus(SpanStatus.INTERNAL_ERROR);
        throw e;
      } finally {
        span.finish();
      }
    } finally {
      addBreadcrumb(request, body, responseStatusCode, response);
    }
  }

  private void addBreadcrumb(
      final @NotNull HttpRequest request,
      final @NotNull byte[] body,
      final @Nullable Integer responseStatusCode,
      final @Nullable ClientHttpResponse response) {
    final String methodName = request.getMethod() != null ? request.getMethod().name() : "unknown";

    final Breadcrumb breadcrumb =
        Breadcrumb.http(request.getURI().toString(), methodName, responseStatusCode);
    breadcrumb.setData("request_body_size", body.length);

    final Hint hint = new Hint();
    hint.set(SPRING_REQUEST_INTERCEPTOR_REQUEST, request);
    hint.set(SPRING_REQUEST_INTERCEPTOR_REQUEST_BODY, body);
    if (response != null) {
      hint.set(SPRING_REQUEST_INTERCEPTOR_RESPONSE, response);
    }

    hub.addBreadcrumb(breadcrumb, hint);
  }
}
