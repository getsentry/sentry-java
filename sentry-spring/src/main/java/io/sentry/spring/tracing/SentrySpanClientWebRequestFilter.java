package io.sentry.spring.tracing;

import static io.sentry.TypeCheckHint.SPRING_EXCHANGE_FILTER_REQUEST;
import static io.sentry.TypeCheckHint.SPRING_EXCHANGE_FILTER_RESPONSE;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.BaggageHeader;
import io.sentry.Breadcrumb;
import io.sentry.Hint;
import io.sentry.IHub;
import io.sentry.ISpan;
import io.sentry.SentryTraceHeader;
import io.sentry.SpanStatus;
import io.sentry.TracingOrigins;
import io.sentry.util.Objects;
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
      addBreadcrumb(request, null);
      return next.exchange(request);
    }

    final ISpan span = activeSpan.startChild("http.client");
    span.setDescription(request.method().name() + " " + request.url());

    final SentryTraceHeader sentryTraceHeader = span.toSentryTrace();
    final @Nullable BaggageHeader baggageHeader = span.toBaggageHeader();

    final ClientRequest.Builder requestBuilder = ClientRequest.from(request);

    if (TracingOrigins.contain(hub.getOptions().getTracingOrigins(), request.url())) {
      requestBuilder.header(sentryTraceHeader.getName(), sentryTraceHeader.getValue());
      if (baggageHeader != null) {
        requestBuilder.header(baggageHeader.getName(), baggageHeader.getValue());
      }
    }

    final ClientRequest clientRequestWithSentryTraceHeader = requestBuilder.build();

    return next.exchange(clientRequestWithSentryTraceHeader)
        .flatMap(
            response -> {
              span.setStatus(SpanStatus.fromHttpStatusCode(response.rawStatusCode()));
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

  private void addBreadcrumb(
      final @NotNull ClientRequest request, final @Nullable ClientResponse response) {
    final Breadcrumb breadcrumb =
        Breadcrumb.http(
            request.url().toString(),
            request.method().name(),
            response != null ? response.rawStatusCode() : null);

    final Hint hint = new Hint();
    hint.set(SPRING_EXCHANGE_FILTER_REQUEST, request);
    if (response != null) {
      hint.set(SPRING_EXCHANGE_FILTER_RESPONSE, response);
    }

    hub.addBreadcrumb(breadcrumb, hint);
  }
}
