package io.sentry;

import io.sentry.util.Objects;
import java.util.Random;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

final class TracingSampler {
  private final @NotNull SentryOptions options;
  private final @NotNull Random random;

  public TracingSampler(final @NotNull SentryOptions options) {
    this(Objects.requireNonNull(options, "options are required"), new Random());
  }

  @TestOnly
  TracingSampler(final @NotNull SentryOptions options, final @NotNull Random random) {
    this.options = options;
    this.random = random;
  }

  boolean sample(final @Nullable SamplingContext samplingContext) {
    if (samplingContext != null && samplingContext.getTransactionContexts().getSampled() != null) {
      return samplingContext.getTransactionContexts().getSampled();
    } else if (samplingContext != null && options.getTracesSampler() != null) {
      return sample(options.getTracesSampler().sample(samplingContext));
    } else if (samplingContext != null
        && samplingContext.getTransactionContexts().getParentSampled() != null) {
      return samplingContext.getTransactionContexts().getParentSampled();
    } else if (options.getTracesSampleRate() != null) {
      return sample(options.getTracesSampleRate());
    } else {
      return false;
    }
  }

  private boolean sample(final @NotNull Double aDouble) {
    return !(aDouble < random.nextDouble());
  }
}
