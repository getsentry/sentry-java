package io.sentry;

import io.sentry.util.Objects;
import java.security.SecureRandom;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

final class TracesSampler {
  private final @NotNull SentryOptions options;
  private final @NotNull SecureRandom random;

  public TracesSampler(final @NotNull SentryOptions options) {
    this(Objects.requireNonNull(options, "options are required"), new SecureRandom());
  }

  @TestOnly
  TracesSampler(final @NotNull SentryOptions options, final @NotNull SecureRandom random) {
    this.options = options;
    this.random = random;
  }

  @NotNull
  TracesSamplingDecision sample(final @NotNull SamplingContext samplingContext) {
    TracesSamplingDecision samplingContextSamplingDecision =
        samplingContext.getTransactionContext().getSamplingDecision();
    if (samplingContextSamplingDecision != null) {
      return samplingContextSamplingDecision;
    }

    if (options.getTracesSampler() != null) {
      final Double samplerResult = options.getTracesSampler().sample(samplingContext);
      if (samplerResult != null) {
        return new TracesSamplingDecision(sample(samplerResult), samplerResult);
      }
    }

    TracesSamplingDecision parentSamplingDecision =
        samplingContext.getTransactionContext().getParentSamplingDecision();
    if (parentSamplingDecision != null) {
      return parentSamplingDecision;
    }

    Double tracesSampleRateFromOptions = options.getTracesSampleRate();
    if (tracesSampleRateFromOptions != null) {
      return new TracesSamplingDecision(
          sample(tracesSampleRateFromOptions), tracesSampleRateFromOptions);
    }

    return new TracesSamplingDecision(false);
  }

  private boolean sample(final @NotNull Double aDouble) {
    return !(aDouble < random.nextDouble());
  }
}
