package io.sentry.spring7.tracing;

import io.sentry.protocol.TransactionNameSource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves transaction name using other transaction name providers by invoking them in order. If a
 * provider returns no transaction name, the next one is invoked.
 */
@ApiStatus.Internal
public final class CombinedTransactionNameProvider implements TransactionNameProvider {

  private final @NotNull List<TransactionNameProvider> providers;

  public CombinedTransactionNameProvider(final @NotNull List<TransactionNameProvider> providers) {
    this.providers = providers;
  }

  @Override
  public @Nullable String provideTransactionName(@NotNull HttpServletRequest request) {
    for (TransactionNameProvider provider : providers) {
      String transactionName = provider.provideTransactionName(request);
      if (transactionName != null) {
        return transactionName;
      }
    }

    return null;
  }

  @Override
  @ApiStatus.Internal
  public @NotNull TransactionNameSource provideTransactionSource() {
    return TransactionNameSource.CUSTOM;
  }

  @ApiStatus.Internal
  @Override
  public @NotNull TransactionNameWithSource provideTransactionNameAndSource(
      @NotNull HttpServletRequest request) {
    for (TransactionNameProvider provider : providers) {
      String transactionName = provider.provideTransactionName(request);
      if (transactionName != null) {
        final @NotNull TransactionNameSource source = provider.provideTransactionSource();
        return new TransactionNameWithSource(transactionName, source);
      }
    }

    return new TransactionNameWithSource(null, TransactionNameSource.CUSTOM);
  }
}
