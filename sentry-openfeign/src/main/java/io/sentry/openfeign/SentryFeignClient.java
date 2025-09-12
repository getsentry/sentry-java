package io.sentry.openfeign;

import static io.sentry.TypeCheckHint.OPEN_FEIGN_REQUEST;
import static io.sentry.TypeCheckHint.OPEN_FEIGN_RESPONSE;
import static io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion;

import feign.Client;
import feign.Request;
import feign.Response;
import io.sentry.BaggageHeader;
import io.sentry.Breadcrumb;
import io.sentry.BuildConfig;
import io.sentry.Hint;
import io.sentry.IScopes;
import io.sentry.ISpan;
import io.sentry.SentryIntegrationPackageStorage;
import io.sentry.SpanDataConvention;
import io.sentry.SpanOptions;
import io.sentry.SpanStatus;
import io.sentry.W3CTraceparentHeader;
import io.sentry.util.Objects;
import io.sentry.util.SpanUtils;
import io.sentry.util.TracingUtils;
import io.sentry.util.UrlUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A Feign client that creates a span around each executed HTTP call. */
public final class SentryFeignClient implements Client {
  private static final String TRACE_ORIGIN = "auto.http.openfeign";
  private final @NotNull Client delegate;
  private final @NotNull IScopes scopes;
  private final @Nullable BeforeSpanCallback beforeSpan;

  static {
    SentryIntegrationPackageStorage.getInstance()
        .addPackage("maven:io.sentry:sentry-openfeign", BuildConfig.VERSION_NAME);
  }

  public SentryFeignClient(
      final @NotNull Client delegate,
      final @NotNull IScopes scopes,
      final @Nullable BeforeSpanCallback beforeSpan) {
    this.delegate = Objects.requireNonNull(delegate, "delegate is required");
    this.scopes = Objects.requireNonNull(scopes, "scopes are required");
    this.beforeSpan = beforeSpan;
    addPackageAndIntegrationInfo();
  }

  private void addPackageAndIntegrationInfo() {
    addIntegrationToSdkVersion("OpenFeign");
  }

  @Override
  public Response execute(final @NotNull Request request, final @NotNull Request.Options options)
      throws IOException {
    Response response = null;
    try {
      final ISpan activeSpan = scopes.getSpan();

      if (activeSpan == null) {
        final @NotNull Request modifiedRequest = maybeAddTracingHeaders(request, null);
        return delegate.execute(modifiedRequest, options);
      }

      final @NotNull SpanOptions spanOptions = new SpanOptions();
      spanOptions.setOrigin(TRACE_ORIGIN);
      ISpan span = activeSpan.startChild("http.client", null, spanOptions);
      final @NotNull UrlUtils.UrlDetails urlDetails = UrlUtils.parse(request.url());
      final @NotNull String method = request.httpMethod().name();
      span.setDescription(method + " " + urlDetails.getUrlOrFallback());
      span.setData(SpanDataConvention.HTTP_METHOD_KEY, method.toUpperCase(Locale.ROOT));
      urlDetails.applyToSpan(span);

      final @NotNull Request modifiedRequest = maybeAddTracingHeaders(request, span);

      try {
        response = delegate.execute(modifiedRequest, options);
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

  private @NotNull Request maybeAddTracingHeaders(
      final @NotNull Request request, final @Nullable ISpan span) {
    if (isIgnored()) {
      return request;
    }

    final @NotNull RequestWrapper requestWrapper = new RequestWrapper(request);
    final @Nullable Collection<String> requestBaggageHeaders =
        request.headers().get(BaggageHeader.BAGGAGE_HEADER);

    final @Nullable TracingUtils.TracingHeaders tracingHeaders =
        TracingUtils.traceIfAllowed(
            scopes,
            request.url(),
            (requestBaggageHeaders != null ? new ArrayList<>(requestBaggageHeaders) : null),
            span);

    if (tracingHeaders != null) {
      requestWrapper.header(
          tracingHeaders.getSentryTraceHeader().getName(),
          tracingHeaders.getSentryTraceHeader().getValue());

      final @Nullable BaggageHeader baggageHeader = tracingHeaders.getBaggageHeader();
      if (baggageHeader != null) {
        requestWrapper.removeHeader(BaggageHeader.BAGGAGE_HEADER);
        requestWrapper.header(baggageHeader.getName(), baggageHeader.getValue());
      }

      final @Nullable W3CTraceparentHeader w3cTraceparentHeader =
          tracingHeaders.getW3cTraceparentHeader();
      if (w3cTraceparentHeader != null) {
        requestWrapper.header(w3cTraceparentHeader.getName(), w3cTraceparentHeader.getValue());
      }
    }

    return requestWrapper.build();
  }

  private boolean isIgnored() {
    return SpanUtils.isIgnored(scopes.getOptions().getIgnoredSpanOrigins(), TRACE_ORIGIN);
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

    scopes.addBreadcrumb(breadcrumb, hint);
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
