package io.sentry.opentelemetry;

import io.sentry.protocol.TransactionNameSource;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class OtelSpanInfo {

  private final @NotNull String op;
  private final @Nullable String description;
  private final @NotNull TransactionNameSource transactionNameSource;

  public OtelSpanInfo(
      final @NotNull String op,
      final @Nullable String description,
      final @NotNull TransactionNameSource transactionNameSource) {
    this.op = op;
    this.description = description;
    this.transactionNameSource = transactionNameSource;
  }

  public @NotNull String getOp() {
    return op;
  }

  public @Nullable String getDescription() {
    return description;
  }

  public @NotNull TransactionNameSource getTransactionNameSource() {
    return transactionNameSource;
  }
}
