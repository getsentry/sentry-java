package io.sentry.spring.jakarta.webflux;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;

/**
 * Resolves transaction name using {@link ServerWebExchange#getRequest()} ()} and templated route
 * that handled the request. To return correct transaction name, it must be used after request is
 * processed by {@link
 * org.springframework.web.reactive.result.method.RequestMappingInfoHandlerMapping} where {@link
 * HandlerMapping#BEST_MATCHING_PATTERN_ATTRIBUTE} is set.
 */
final class TransactionNameProvider {
  static @Nullable String provideTransactionName(
      final @NotNull ServerWebExchange serverWebExchange) {
    final PathPattern pattern =
        serverWebExchange.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);

    if (pattern != null) {
      final String methodName =
          serverWebExchange.getRequest().getMethod() != null
              ? serverWebExchange.getRequest().getMethod().name()
              : "unknown";
      return methodName + " " + pattern.getPatternString();
    } else {
      return null;
    }
  }
}
