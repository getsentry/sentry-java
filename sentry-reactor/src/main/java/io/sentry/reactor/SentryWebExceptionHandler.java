package io.sentry.reactor;

import io.sentry.IHub;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.exception.ExceptionMechanismException;
import io.sentry.protocol.Mechanism;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.annotation.Order;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

@Order(
    -2) // the DefaultErrorWebExceptionHandler provided by Spring Boot for error handling is ordered
// at -1
public final class SentryWebExceptionHandler implements WebExceptionHandler {
  private final @NotNull IHub hub;
  private final @NotNull TransactionNameProvider transactionNameProvider;

  public SentryWebExceptionHandler(final @NotNull IHub hub) {
    this.hub = hub;
    this.transactionNameProvider = new TransactionNameProvider();
  }

  @Override
  public @NotNull Mono<Void> handle(
      final @NotNull ServerWebExchange serverWebExchange, final @NotNull Throwable ex) {
    if (!(ex instanceof ResponseStatusException)) {
      final Mechanism mechanism = new Mechanism();
      mechanism.setHandled(false);
      final Throwable throwable =
          new ExceptionMechanismException(mechanism, ex, Thread.currentThread());
      final SentryEvent event = new SentryEvent(throwable);
      event.setLevel(SentryLevel.FATAL);
      event.setTransaction(transactionNameProvider.provideTransactionName(serverWebExchange));
      hub.captureEvent(event);
    }
    return Mono.error(ex);
  }
}
