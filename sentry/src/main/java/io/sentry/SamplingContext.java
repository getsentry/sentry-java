package io.sentry;

import io.sentry.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Context used by {@link io.sentry.SentryOptions.TracesSamplerCallback} to determine if transaction
 * is going to be sampled.
 */
public final class SamplingContext {
  private final @NotNull TransactionContexts transactionContexts;
  private final @Nullable CustomSamplingContext customSamplingContext;
  private final @Nullable Boolean parentSampled;

  public SamplingContext(
      final @NotNull TransactionContexts transactionContexts,
      final @Nullable CustomSamplingContext customSamplingContext,
      final @Nullable Boolean parentSampled) {
    this.transactionContexts =
        Objects.requireNonNull(transactionContexts, "transactionContexts is required");
    this.customSamplingContext = customSamplingContext;
    this.parentSampled = parentSampled;
  }

  public @Nullable CustomSamplingContext getCustomSamplingContext() {
    return customSamplingContext;
  }

  public @NotNull TransactionContexts getTransactionContexts() {
    return transactionContexts;
  }

  public @Nullable Boolean getParentSampled() {
    return parentSampled;
  }
}
