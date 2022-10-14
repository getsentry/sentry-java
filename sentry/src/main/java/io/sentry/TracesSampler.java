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
    final TracesSamplingDecision samplingContextSamplingDecision =
        samplingContext.getTransactionContext().getSamplingDecision();
    if (samplingContextSamplingDecision != null) {
      return samplingContextSamplingDecision;
    }

    Double profilesSampleRate = null;
    if (options.getProfilesSampler() != null) {
      try {
        profilesSampleRate = options.getProfilesSampler().sample(samplingContext);
      } catch (Throwable t) {
        options
            .getLogger()
            .log(SentryLevel.ERROR, "Error in the 'ProfilesSamplerCallback' callback.", t);
      }
    }
    if (profilesSampleRate == null) {
      profilesSampleRate = options.getProfilesSampleRate();
    }
    Boolean profilesSampled = profilesSampleRate != null && sample(profilesSampleRate);

    if (options.getTracesSampler() != null) {
      Double samplerResult = null;
      try {
        samplerResult = options.getTracesSampler().sample(samplingContext);
      } catch (Throwable t) {
        options
            .getLogger()
            .log(SentryLevel.ERROR, "Error in the 'TracesSamplerCallback' callback.", t);
      }
      if (samplerResult != null) {
        return new TracesSamplingDecision(
            sample(samplerResult), samplerResult, profilesSampled, profilesSampleRate);
      }
    }

    final TracesSamplingDecision parentSamplingDecision =
        samplingContext.getTransactionContext().getParentSamplingDecision();
    if (parentSamplingDecision != null) {
      return parentSamplingDecision;
    }

    final Double tracesSampleRateFromOptions = options.getTracesSampleRate();
    if (tracesSampleRateFromOptions != null) {
      return new TracesSamplingDecision(
          sample(tracesSampleRateFromOptions),
          tracesSampleRateFromOptions,
          profilesSampled,
          profilesSampleRate);
    }

    return new TracesSamplingDecision(false, null, false, null);
  }

  private boolean sample(final @NotNull Double aDouble) {
    return !(aDouble < random.nextDouble());
  }
}
