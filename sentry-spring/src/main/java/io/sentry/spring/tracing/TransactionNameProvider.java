package io.sentry.spring.tracing;

import javax.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves transaction name from {@link HttpServletRequest}.
 *
 * <p>With Spring MVC - use {@link SpringMvcTransactionNameProvider}.
 */
public interface TransactionNameProvider {
  /**
   * Resolves transaction name from {@link HttpServletRequest}.
   *
   * @param request - the http request
   * @return transaction name or {@code null} if not resolved
   */
  @Nullable
  String provideTransactionName(@NotNull HttpServletRequest request);
}
