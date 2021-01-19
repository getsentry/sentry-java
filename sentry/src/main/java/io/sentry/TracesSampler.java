package io.sentry;

import io.sentry.util.Objects;
import java.util.Random;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

final class TracesSampler {
  private final @NotNull SentryOptions options;
  private final @NotNull Random random;

  public TracesSampler(final @NotNull SentryOptions options) {
    this(Objects.requireNonNull(options, "options are required"), new Random());
  }

  @TestOnly
  TracesSampler(final @NotNull SentryOptions options, final @NotNull Random random) {
    this.options = options;
    this.random = random;
  }

  boolean sample(final @NotNull SamplingContext samplingContext) {
    if (samplingContext.getTransactionContext().getSampled() != null) {
      return samplingContext.getTransactionContext().getSampled();
    } else if (options.getTracesSampler() != null) {
      return sample(options.getTracesSampler().sample(samplingContext));
    } else if (samplingContext.getTransactionContext().getParentSampled() != null) {
      return samplingContext.getTransactionContext().getParentSampled();
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
