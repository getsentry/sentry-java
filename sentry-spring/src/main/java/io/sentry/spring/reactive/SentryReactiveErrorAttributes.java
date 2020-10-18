package io.sentry.spring.reactive;

import com.jakewharton.nopen.annotation.Open;
import org.springframework.boot.web.reactive.error.DefaultErrorAttributes;
import org.springframework.web.server.ServerWebExchange;

@Open
public class SentryReactiveErrorAttributes extends DefaultErrorAttributes {

  @Override
  public void storeErrorInformation(Throwable error, ServerWebExchange exchange) {
    SentryReactiveWebHelper.withRequestHub(exchange, iHub -> iHub.captureException(error));
    super.storeErrorInformation(error, exchange);
  }
}
