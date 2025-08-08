package io.sentry.spring.jakarta.tracing;

import io.sentry.protocol.TransactionNameSource;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class TransactionNameWithSource {
  private final @Nullable String transactionName;
  private final @NotNull TransactionNameSource transactionNameSource;

  public TransactionNameWithSource(
      final @Nullable String transactionName,
      final @NotNull TransactionNameSource transactionNameSource) {
    this.transactionName = transactionName;
    this.transactionNameSource = transactionNameSource;
  }

  public @Nullable String getTransactionName() {
    return transactionName;
  }

  public @NotNull TransactionNameSource getTransactionNameSource() {
    return transactionNameSource;
  }
}
