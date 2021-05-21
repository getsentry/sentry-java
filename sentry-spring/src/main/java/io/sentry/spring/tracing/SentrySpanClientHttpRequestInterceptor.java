package io.sentry.spring.tracing;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.Breadcrumb;
import io.sentry.IHub;
import io.sentry.ISpan;
import io.sentry.SentryTraceHeader;
import io.sentry.SpanStatus;
import io.sentry.util.Objects;
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
    try {
      final ISpan activeSpan = hub.getSpan();
      if (activeSpan == null) {
        return execution.execute(request, body);
      }

      final ISpan span = activeSpan.startChild("http.client");
      span.setDescription(request.getMethodValue() + " " + request.getURI());

      final SentryTraceHeader sentryTraceHeader = span.toSentryTrace();
      request.getHeaders().add(sentryTraceHeader.getName(), sentryTraceHeader.getValue());
      try {
        final ClientHttpResponse response = execution.execute(request, body);
        // handles both success and error responses
        span.setStatus(SpanStatus.fromHttpStatusCode(response.getRawStatusCode()));
        responseStatusCode = response.getRawStatusCode();
        return response;
      } catch (Exception e) {
        // handles cases like connection errors
        span.setThrowable(e);
        span.setStatus(SpanStatus.INTERNAL_ERROR);
        throw e;
      } finally {
        span.finish();
      }
    } finally {
      addBreadcrumb(request, body, responseStatusCode);
    }
  }

  private void addBreadcrumb(
      final @NotNull HttpRequest request,
      final @NotNull byte[] body,
      final @Nullable Integer responseStatusCode) {
    final Breadcrumb breadcrumb =
        Breadcrumb.http(request.getURI().toString(), request.getMethodValue(), responseStatusCode);
    breadcrumb.setData("requestBodySize", body.length);
    hub.addBreadcrumb(breadcrumb);
  }
}
