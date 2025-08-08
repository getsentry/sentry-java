package io.sentry.spring.jakarta.tracing;

import io.sentry.protocol.TransactionNameSource;
import jakarta.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Fallback TransactionNameProvider when Spring is used in servlet mode (without MVC). */
@ApiStatus.Internal
public final class SpringServletTransactionNameProvider implements TransactionNameProvider {
  @Override
  public @Nullable String provideTransactionName(final @NotNull HttpServletRequest request) {
    return request.getMethod() + " " + request.getRequestURI();
  }

  @Override
  @ApiStatus.Internal
  public @NotNull TransactionNameSource provideTransactionSource() {
    return TransactionNameSource.URL;
  }
}
