package io.sentry.spring.jakarta.tracing;

import io.sentry.protocol.TransactionNameSource;
import jakarta.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Resolves transaction name using {@link HttpServletRequest#getMethod()} and templated route that
 * handled the request. To return correct transaction name, it must be used after request is
 * processed by {@link org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping}
 * where {@link HandlerMapping#BEST_MATCHING_PATTERN_ATTRIBUTE} is set.
 */
@ApiStatus.Internal
public final class SpringMvcTransactionNameProvider implements TransactionNameProvider {
  @Override
  public @Nullable String provideTransactionName(final @NotNull HttpServletRequest request) {
    final String pattern =
        (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);

    if (pattern != null) {
      return request.getMethod() + " " + pattern;
    } else {
      return null;
    }
  }

  @Override
  @ApiStatus.Internal
  public @NotNull TransactionNameSource provideTransactionSource() {
    return TransactionNameSource.ROUTE;
  }
}
