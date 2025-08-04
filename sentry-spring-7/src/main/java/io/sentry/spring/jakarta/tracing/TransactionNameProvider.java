package io.sentry.spring.jakarta.tracing;

import io.sentry.protocol.TransactionNameSource;
import jakarta.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.ApiStatus;
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

  /** Returns the source of the transaction name. Only to be used internally. */
  @NotNull
  @ApiStatus.Internal
  default TransactionNameSource provideTransactionSource() {
    return TransactionNameSource.CUSTOM;
  }

  @NotNull
  @ApiStatus.Internal
  default TransactionNameWithSource provideTransactionNameAndSource(
      final @NotNull HttpServletRequest request) {
    return new TransactionNameWithSource(
        provideTransactionName(request), provideTransactionSource());
  }
}
