package io.sentry.spring.reactive;

import com.jakewharton.nopen.annotation.Open;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

@Open
public class SentryReactiveExceptionHandler implements WebExceptionHandler, Ordered {

  @Override
  public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
    SentryReactiveWebHelper.withRequestHub(exchange, hub -> hub.captureException(ex));
    // let other WebExceptionHandlers handle the exception
    return Mono.error(ex);
  }

  @Override
  public int getOrder() {
    // ensure this resolver runs with the highest precedence so that all exceptions are reported
    return Ordered.HIGHEST_PRECEDENCE;
  }
}
