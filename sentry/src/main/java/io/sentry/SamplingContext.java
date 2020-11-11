package io.sentry;

import io.sentry.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Context used by {@link io.sentry.SentryOptions.TracesSamplerCallback} to determine if transaction
 * is going to be sampled.
 */
public final class SamplingContext {
  private final @NotNull TransactionContext transactionContexts;
  private final @Nullable CustomSamplingContext customSamplingContext;

  public SamplingContext(
      final @NotNull TransactionContext transactionContexts,
      final @Nullable CustomSamplingContext customSamplingContext) {
    this.transactionContexts =
        Objects.requireNonNull(transactionContexts, "transactionContexts is required");
    this.customSamplingContext = customSamplingContext;
  }

  public @Nullable CustomSamplingContext getCustomSamplingContext() {
    return customSamplingContext;
  }

  public @NotNull TransactionContext getTransactionContexts() {
    return transactionContexts;
  }
}
