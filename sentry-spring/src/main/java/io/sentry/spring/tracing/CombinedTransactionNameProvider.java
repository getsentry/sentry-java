package io.sentry.spring.tracing;

import io.sentry.protocol.TransactionNameSource;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves transaction name using {@link HttpServletRequest#getMethod()} and templated route that
 * handled the request. To return correct transaction name, it must be used after request is
 * processed by {@link org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping}
 * where {@link org.springframework.web.servlet.HandlerMapping#BEST_MATCHING_PATTERN_ATTRIBUTE} is
 * set.
 */
@ApiStatus.Internal
public final class CombinedTransactionNameProvider implements TransactionNameProvider {

  private final @NotNull List<TransactionNameProvider> providers;
  private @Nullable TransactionNameSource source = null;

  public CombinedTransactionNameProvider(final @NotNull List<TransactionNameProvider> providers) {
    this.providers = providers;
  }

  @Override
  public @Nullable String provideTransactionName(final @NotNull HttpServletRequest request) {
    for (TransactionNameProvider provider : providers) {
      String transactionName = provider.provideTransactionName(request);
      if (transactionName != null) {
        source = provider.provideTransactionSource();
        return transactionName;
      }
    }

    return null;
  }

  @Override
  @ApiStatus.Internal
  public @NotNull TransactionNameSource provideTransactionSource() {
    final @Nullable TransactionNameSource tmpSource = source;
    return tmpSource == null ? TransactionNameSource.CUSTOM : tmpSource;
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
