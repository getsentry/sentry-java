package io.sentry.spring.webflux;

import io.sentry.IHub;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.exception.ExceptionMechanismException;
import io.sentry.protocol.Mechanism;
import io.sentry.util.Objects;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.annotation.Order;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

/** Handles unhandled exceptions in Spring WebFlux integration. */
@Order(
    -2) // the DefaultErrorWebExceptionHandler provided by Spring Boot for error handling is ordered
// at -1
@ApiStatus.Experimental
public final class SentryWebExceptionHandler implements WebExceptionHandler {
  private final @NotNull IHub hub;

  public SentryWebExceptionHandler(final @NotNull IHub hub) {
    this.hub = Objects.requireNonNull(hub, "hub is required");
  }

  @Override
  public @NotNull Mono<Void> handle(
      final @NotNull ServerWebExchange serverWebExchange, final @NotNull Throwable ex) {
    if (!(ex instanceof ResponseStatusException)) {
      final Mechanism mechanism = new Mechanism();
      mechanism.setType("SentryWebExceptionHandler");
      mechanism.setHandled(false);
      final Throwable throwable =
          new ExceptionMechanismException(mechanism, ex, Thread.currentThread());
      final SentryEvent event = new SentryEvent(throwable);
      event.setLevel(SentryLevel.FATAL);
      event.setTransaction(TransactionNameProvider.provideTransactionName(serverWebExchange));
      hub.captureEvent(event);
    }
    return Mono.error(ex);
  }
}
