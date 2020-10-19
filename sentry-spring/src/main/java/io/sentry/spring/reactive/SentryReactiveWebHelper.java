package io.sentry.spring.reactive;

import io.sentry.IHub;
import io.sentry.ILogger;
import io.sentry.SentryLevel;
import io.sentry.SystemOutLogger;
import java.util.function.Consumer;
import org.springframework.web.server.ServerWebExchange;

public final class SentryReactiveWebHelper {
  static final String REQUEST_HUB_ATTR_NAME = "SentryReactiveWebHelper.REQUEST_HUB_ATTR_NAME";

  private static final ILogger LOGGER = new SystemOutLogger();

  public static void withRequestHub(ServerWebExchange exchange, Consumer<IHub> hubConsumer) {
    Object ihub = exchange.getAttributes().get(REQUEST_HUB_ATTR_NAME);
    if (ihub instanceof IHub) {
      hubConsumer.accept((IHub) ihub);
    } else {
      LOGGER.log(SentryLevel.ERROR, "No Hub configured in ServerWebExchange");
    }
  }
}
