package io.sentry.spring.reactive;

import static reactor.core.publisher.Flux.fromIterable;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.IHub;
import io.sentry.protocol.User;
import io.sentry.spring.SentryUserProviderEventProcessor;
import io.sentry.util.Objects;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Open
public class SentryReactiveHubAdapter {

  private final @NotNull IHub hub;
  private final @NotNull List<SentryReactiveUserProvider> sentryUserProviders;
  private final @NotNull ServerWebExchange exchange;

  public SentryReactiveHubAdapter(
      final @NotNull IHub hub,
      final @NotNull List<SentryReactiveUserProvider> sentryUserProviders,
      final @NotNull ServerWebExchange exchange) {
    this.hub = Objects.requireNonNull(hub, "hub is required");
    this.sentryUserProviders =
        Objects.requireNonNull(sentryUserProviders, "User providers are required");
    this.exchange = Objects.requireNonNull(exchange, "Exchange is required");
  }

  public Mono<Void> captureWith(final @NotNull Consumer<IHub> consumer) {
    return processProviders(exchange)
        .doOnNext(
            users -> {
              addEventProcessors(hub, users);
              consumer.accept(hub);
            })
        .then();
  }

  public @NotNull IHub getHub() {
    return hub;
  }

  private Mono<List<User>> processProviders(ServerWebExchange exchange) {
    return fromIterable(sentryUserProviders)
        .flatMap(userProvider -> userProvider.provideUser(exchange))
        .collectList()
        .defaultIfEmpty(Collections.emptyList());
  }

  private static void addEventProcessors(IHub hub, List<User> users) {
    hub.configureScope(
        scope ->
            users.forEach(
                user -> scope.addEventProcessor(new SentryUserProviderEventProcessor(() -> user))));
  }
}
