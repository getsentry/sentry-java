package io.sentry.spring.jakarta.tracing;

import static io.sentry.TypeCheckHint.SPRING_EXCHANGE_FILTER_REQUEST;
import static io.sentry.TypeCheckHint.SPRING_EXCHANGE_FILTER_RESPONSE;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.BaggageHeader;
import io.sentry.Breadcrumb;
import io.sentry.Hint;
import io.sentry.IHub;
import io.sentry.ISpan;
import io.sentry.SpanStatus;
import io.sentry.util.Objects;
import io.sentry.util.TracingUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

@Open
public class SentrySpanClientWebRequestFilter implements ExchangeFilterFunction {
  private final @NotNull IHub hub;

  public SentrySpanClientWebRequestFilter(final @NotNull IHub hub) {
    this.hub = Objects.requireNonNull(hub, "hub is required");
  }

  @Override
  public @NotNull Mono<ClientResponse> filter(
      final @NotNull ClientRequest request, final @NotNull ExchangeFunction next) {
    final ISpan activeSpan = hub.getSpan();
    if (activeSpan == null) {
      final @NotNull ClientRequest modifiedRequest = maybeAddTracingHeaders(request, null);
      addBreadcrumb(modifiedRequest, null);
      return next.exchange(modifiedRequest);
    }

    final ISpan span = activeSpan.startChild("http.client");
    span.setDescription(request.method().name() + " " + request.url());

    final @NotNull ClientRequest modifiedRequest = maybeAddTracingHeaders(request, span);

    return next.exchange(modifiedRequest)
        .flatMap(
            response -> {
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

  private @NotNull ClientRequest maybeAddTracingHeaders(
      final @NotNull ClientRequest request, final @Nullable ISpan span) {
    final ClientRequest.Builder requestBuilder = ClientRequest.from(request);

    TracingUtils.traceIfAllowed(
        hub,
        request.url().toString(),
        request.headers().get(BaggageHeader.BAGGAGE_HEADER),
        span,
        tracingHeaders -> {
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
        });

    return requestBuilder.build();
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

    hub.addBreadcrumb(breadcrumb, hint);
  }
}
