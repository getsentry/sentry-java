package io.sentry;

import io.sentry.util.Objects;
import io.sentry.util.SentryRandom;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Context used by {@link io.sentry.SentryOptions.TracesSamplerCallback} to determine if transaction
 * is going to be sampled.
 */
public final class SamplingContext {
  private final @NotNull TransactionContext transactionContext;
  private final @Nullable CustomSamplingContext customSamplingContext;
  private final @NotNull Double sampleRand;

  @Deprecated
  @SuppressWarnings("InlineMeSuggester")
  /**
   * @deprecated creating a SamplingContext is something only the SDK should do
   */
  public SamplingContext(
      final @NotNull TransactionContext transactionContext,
      final @Nullable CustomSamplingContext customSamplingContext) {
    this(transactionContext, customSamplingContext, SentryRandom.current().nextDouble());
  }

  @ApiStatus.Internal
  public SamplingContext(
      final @NotNull TransactionContext transactionContext,
      final @Nullable CustomSamplingContext customSamplingContext,
      final @NotNull Double sampleRand) {
    this.transactionContext =
        Objects.requireNonNull(transactionContext, "transactionContexts is required");
    this.customSamplingContext = customSamplingContext;
    this.sampleRand = sampleRand;
  }

  public @Nullable CustomSamplingContext getCustomSamplingContext() {
    return customSamplingContext;
  }

  public @NotNull TransactionContext getTransactionContext() {
    return transactionContext;
  }

  public @NotNull Double getSampleRand() {
    return sampleRand;
  }
}
