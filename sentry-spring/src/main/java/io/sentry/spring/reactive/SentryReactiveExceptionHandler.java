package io.sentry.spring.reactive;

import static io.sentry.spring.reactive.SentryReactiveWebHelper.captureWithRequestHub;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.spring.common.CaptureHelper;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

@Open
public class SentryReactiveExceptionHandler implements WebExceptionHandler, Ordered {

  @Override
  public @NotNull Mono<Void> handle(
      final @NotNull ServerWebExchange exchange, final @NotNull Throwable ex) {
    return captureWithRequestHub(exchange, hub -> CaptureHelper.captureUnhandled(hub, ex))
        .then(Mono.error(ex));
  }

  @Override
  public int getOrder() {
    // ensure this resolver runs with the highest precedence so that all exceptions are reported
    return Ordered.HIGHEST_PRECEDENCE;
  }
}
