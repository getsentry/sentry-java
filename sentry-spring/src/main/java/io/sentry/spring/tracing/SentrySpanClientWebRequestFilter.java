package io.sentry.spring.tracing;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.*;
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
  public Mono<ClientResponse> filter(@NotNull ClientRequest request, @NotNull ExchangeFunction next) {
    final ISpan activeSpan = hub.getSpan();
    if (activeSpan == null) {
      addBreadcrumb(request, null);
      return next.exchange(request);
    }

    final ISpan span = activeSpan.startChild("http.client");
    span.setDescription(request.method().name() + " " + request.url());

    final SentryTraceHeader sentryTraceHeader = span.toSentryTrace();

    final ClientRequest clientRequestWithSentryTraceHeader = ClientRequest.from(request)
      .header(sentryTraceHeader.getName(), sentryTraceHeader.getValue())
      .build();

    return next.exchange(clientRequestWithSentryTraceHeader).flatMap(response -> {
      span.setStatus(SpanStatus.fromHttpStatusCode(response.rawStatusCode()));
      addBreadcrumb(request, response.rawStatusCode());
      span.finish();
      return Mono.just(response);
    })
      .onErrorMap(throwable -> {
        span.setThrowable(throwable);
        span.setStatus(SpanStatus.INTERNAL_ERROR);
        addBreadcrumb(request, null);
        span.finish();
        return throwable;
      });
  }

  private void addBreadcrumb(
    final @NotNull ClientRequest request,
    final @Nullable Integer responseStatusCode) {
    final Breadcrumb breadcrumb =
      Breadcrumb.http(request.url().toString(), request.method().name(), responseStatusCode);
    hub.addBreadcrumb(breadcrumb);
  }
}
