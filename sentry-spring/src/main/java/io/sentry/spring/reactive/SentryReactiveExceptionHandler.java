package io.sentry.spring.reactive;

import static io.sentry.spring.reactive.SentryReactiveWebHelper.withRequestHub;
import static reactor.core.publisher.Flux.fromIterable;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.IHub;
import io.sentry.protocol.User;
import io.sentry.spring.SentryUserProviderEventProcessor;
import io.sentry.util.Objects;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

@Open
public class SentryReactiveExceptionHandler implements WebExceptionHandler, Ordered {

  private final @NotNull List<SentryReactiveUserProvider> sentryUserProviders;

  public SentryReactiveExceptionHandler(
      final @NotNull List<SentryReactiveUserProvider> sentryUserProviders) {
    this.sentryUserProviders = Objects.requireNonNull(sentryUserProviders, "options are required");
  }

  @Override
  public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
    return processProviders(exchange)
        .doOnNext(users -> withRequestHub(exchange, hub -> addEventProcessors(hub, users)))
        .then(captureAndContinue(exchange, ex));
  }

  private Mono<List<User>> processProviders(ServerWebExchange exchange) {
    return fromIterable(sentryUserProviders)
        .flatMap(userProvider -> userProvider.provideUser(exchange))
        .collectList();
  }

  private static Mono<Void> captureAndContinue(ServerWebExchange exchange, Throwable ex) {
    withRequestHub(exchange, hub -> hub.captureException(ex));
    return Mono.error(ex);
  }

  private static void addEventProcessors(IHub hub, List<User> users) {
    hub.configureScope(
        scope ->
            users.forEach(
                user -> scope.addEventProcessor(new SentryUserProviderEventProcessor(() -> user))));
  }

  @Override
  public int getOrder() {
    // ensure this resolver runs with the highest precedence so that all exceptions are reported
    return Ordered.HIGHEST_PRECEDENCE;
  }
}
