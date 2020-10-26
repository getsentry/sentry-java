package io.sentry.spring.reactive;

import io.sentry.IHub;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public final class SentryReactiveWebHelper {
  static final String REQUEST_HUB_ADAPTER_NAME = "SentryReactiveWebHelper.REQUEST_HUB_ADAPTER_NAME";

  public static SentryReactiveHubAdapter getSentryReactiveHubAdapter(
      final @NotNull ServerWebExchange exchange) {
    return (SentryReactiveHubAdapter) exchange.getAttributes().get(REQUEST_HUB_ADAPTER_NAME);
  }

  public static Mono<Void> captureWithRequestHub(
      final @NotNull ServerWebExchange exchange, final @NotNull Consumer<IHub> hubConsumer) {
    final SentryReactiveHubAdapter requestHub = getSentryReactiveHubAdapter(exchange);
    return requestHub.captureWith(hubConsumer);
  }

  public static Mono<Void> captureWithRequestHub(final @NotNull Consumer<IHub> hubConsumer) {
    return SentryReactiveHubContextHolder.getHubContext()
        .flatMap(sentryReactiveHubAdapter -> sentryReactiveHubAdapter.captureWith(hubConsumer));
  }
}
