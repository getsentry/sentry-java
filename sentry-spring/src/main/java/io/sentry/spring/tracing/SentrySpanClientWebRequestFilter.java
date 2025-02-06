package io.sentry.spring.tracing;

import static io.sentry.TypeCheckHint.SPRING_EXCHANGE_FILTER_REQUEST;
import static io.sentry.TypeCheckHint.SPRING_EXCHANGE_FILTER_RESPONSE;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.BaggageHeader;
import io.sentry.Breadcrumb;
import io.sentry.Hint;
import io.sentry.IScopes;
import io.sentry.ISpan;
import io.sentry.SpanDataConvention;
import io.sentry.SpanOptions;
import io.sentry.SpanStatus;
import io.sentry.util.Objects;
import io.sentry.util.SpanUtils;
import io.sentry.util.TracingUtils;
import io.sentry.util.UrlUtils;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

@Open
public class SentrySpanClientWebRequestFilter implements ExchangeFilterFunction {
  private static final String TRACE_ORIGIN = "auto.http.spring.webclient";
  private final @NotNull IScopes scopes;

  public SentrySpanClientWebRequestFilter(final @NotNull IScopes scopes) {
    this.scopes = Objects.requireNonNull(scopes, "scopes are required");
  }

  @Override
  public @NotNull Mono<ClientResponse> filter(
      final @NotNull ClientRequest request, final @NotNull ExchangeFunction next) {
    final ISpan activeSpan = scopes.getSpan();
    if (activeSpan == null) {
      addBreadcrumb(request, null);
      return next.exchange(maybeAddHeaders(request, null));
    }
    final @NotNull SpanOptions spanOptions = new SpanOptions();
    spanOptions.setOrigin(TRACE_ORIGIN);
    final ISpan span = activeSpan.startChild("http.client", null, spanOptions);
    final @NotNull UrlUtils.UrlDetails urlDetails = UrlUtils.parse(request.url().toString());
    final @NotNull String method = request.method().name();
    span.setDescription(method + " " + urlDetails.getUrlOrFallback());
    span.setData(SpanDataConvention.HTTP_METHOD_KEY, method.toUpperCase(Locale.ROOT));
    urlDetails.applyToSpan(span);

    final ClientRequest clientRequestWithSentryTraceHeader = maybeAddHeaders(request, span);

    return next.exchange(clientRequestWithSentryTraceHeader)
        .flatMap(
            response -> {
              span.setData(SpanDataConvention.HTTP_STATUS_CODE_KEY, response.statusCode().value());
              span.setStatus(SpanStatus.fromHttpStatusCode(response.statusCode().value()));
              addBreadcrumb(request, response);
              span.finish();
              return Mono.just(response);
            })
        .onErrorMap(
            throwable -> {
              span.setThrowable(throwable);
              span.setStatus(SpanStatus.INTERNAL_ERROR);
              addBreadcrumb(request, null);
              span.finish();
              return throwable;
            });
  }

  private ClientRequest maybeAddHeaders(
      final @NotNull ClientRequest request, final @Nullable ISpan span) {
    if (isIgnored()) {
      return request;
    }

    final ClientRequest.Builder requestBuilder = ClientRequest.from(request);

    final @Nullable TracingUtils.TracingHeaders tracingHeaders =
        TracingUtils.traceIfAllowed(
            scopes,
            request.url().toString(),
            request.headers().get(BaggageHeader.BAGGAGE_HEADER),
            span);

    if (tracingHeaders != null) {
      requestBuilder.header(
          tracingHeaders.getSentryTraceHeader().getName(),
          tracingHeaders.getSentryTraceHeader().getValue());

      final @Nullable BaggageHeader baggageHeader = tracingHeaders.getBaggageHeader();
      if (baggageHeader != null) {
        requestBuilder.headers(
            httpHeaders -> {
              httpHeaders.remove(BaggageHeader.BAGGAGE_HEADER);
              httpHeaders.add(baggageHeader.getName(), baggageHeader.getValue());
            });
      }
    }

    return requestBuilder.build();
  }

  private boolean isIgnored() {
    return SpanUtils.isIgnored(scopes.getOptions().getIgnoredSpanOrigins(), TRACE_ORIGIN);
  }

  private void addBreadcrumb(
      final @NotNull ClientRequest request, final @Nullable ClientResponse response) {
    final Breadcrumb breadcrumb =
        Breadcrumb.http(
            request.url().toString(),
            request.method().name(),
            response != null ? response.statusCode().value() : null);

    final Hint hint = new Hint();
    hint.set(SPRING_EXCHANGE_FILTER_REQUEST, request);
    if (response != null) {
      hint.set(SPRING_EXCHANGE_FILTER_RESPONSE, response);
    }

    scopes.addBreadcrumb(breadcrumb, hint);
  }
}
