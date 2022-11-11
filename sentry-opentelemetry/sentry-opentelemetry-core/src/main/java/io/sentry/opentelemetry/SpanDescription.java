package io.sentry.opentelemetry;

import io.sentry.protocol.TransactionNameSource;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class SpanDescription {

  private final @NotNull String op;
  private final @NotNull String description;
  private final @NotNull TransactionNameSource transactionNameSource;

  public SpanDescription(
      final @NotNull String op,
      final @NotNull String description,
      final @NotNull TransactionNameSource transactionNameSource) {
    this.op = op;
    this.description = description;
    this.transactionNameSource = transactionNameSource;
  }

  public @NotNull String getOp() {
    return op;
  }

  public @NotNull String getDescription() {
    return description;
  }

  public @NotNull TransactionNameSource getTransactionNameSource() {
    return transactionNameSource;
  }
}
