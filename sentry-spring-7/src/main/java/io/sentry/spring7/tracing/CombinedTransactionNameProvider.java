package io.sentry.spring7.tracing;

import static io.sentry.TransactionContext.DEFAULT_TRANSACTION_NAME;

import io.sentry.ScopesAdapter;
import io.sentry.SentryLevel;
import io.sentry.protocol.TransactionNameSource;
import io.sentry.util.ExceptionUtils;
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
  public @Nullable String provideTransactionName(final @NotNull HttpServletRequest request) {
    boolean callbackFailed = false;
    for (final TransactionNameProvider provider : providers) {
      try {
        final @Nullable String transactionName = provider.provideTransactionName(request);
        if (transactionName != null) {
          return transactionName;
        }
      } catch (Throwable e) {
        ExceptionUtils.rethrowIfFatal(e);
        logCallbackError(e);
        callbackFailed = true;
      }
    }
    return callbackFailed ? DEFAULT_TRANSACTION_NAME : null;
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
    boolean callbackFailed = false;
    for (final TransactionNameProvider provider : providers) {
      try {
        final @Nullable String transactionName = provider.provideTransactionName(request);
        if (transactionName != null) {
          return new TransactionNameWithSource(
              transactionName, provider.provideTransactionSource());
        }
      } catch (Throwable e) {
        ExceptionUtils.rethrowIfFatal(e);
        logCallbackError(e);
        callbackFailed = true;
      }
    }
    return callbackFailed
        ? new TransactionNameWithSource(DEFAULT_TRANSACTION_NAME, TransactionNameSource.CUSTOM)
        : new TransactionNameWithSource(null, TransactionNameSource.CUSTOM);
  }

  private static void logCallbackError(final @NotNull Throwable throwable) {
    ScopesAdapter.getInstance()
        .getOptions()
        .getLogger()
        .log(
            SentryLevel.ERROR,
            "The TransactionNameProvider callback threw an exception.",
            throwable);
  }
}
