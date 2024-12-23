package io.sentry;

import io.sentry.util.Objects;
import io.sentry.util.Random;
import io.sentry.util.SentryRandom;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

@ApiStatus.Internal
public final class TracesSampler {
  private final @NotNull SentryOptions options;
  private final @Nullable Random random;

  public TracesSampler(final @NotNull SentryOptions options) {
    this(Objects.requireNonNull(options, "options are required"), null);
  }

  @TestOnly
  TracesSampler(final @NotNull SentryOptions options, final @Nullable Random random) {
    this.options = options;
    this.random = random;
  }

  @SuppressWarnings("deprecation")
  @NotNull
  public TracesSamplingDecision sample(final @NotNull SamplingContext samplingContext) {
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

    final @Nullable Double tracesSampleRateFromOptions = options.getTracesSampleRate();
    final @NotNull Double downsampleFactor =
        Math.pow(2, options.getBackpressureMonitor().getDownsampleFactor());
    final @Nullable Double downsampledTracesSampleRate =
        tracesSampleRateFromOptions == null ? null : tracesSampleRateFromOptions / downsampleFactor;

    if (downsampledTracesSampleRate != null) {
      return new TracesSamplingDecision(
          sample(downsampledTracesSampleRate),
          downsampledTracesSampleRate,
          profilesSampled,
          profilesSampleRate);
    }

    return new TracesSamplingDecision(false, null, false, null);
  }

  public boolean sampleContinuousProfile() {
    final double sampling = options.getContinuousProfilesSampleRate();
    return sample(sampling);
  }

  private boolean sample(final @NotNull Double aDouble) {
    return !(aDouble < getRandom().nextDouble());
  }

  private Random getRandom() {
    if (random == null) {
      return SentryRandom.current();
    }
    return random;
  }
}
