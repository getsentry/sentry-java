package io.sentry;

import io.sentry.util.Objects;
import io.sentry.util.Random;
import io.sentry.util.SentryRandom;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

final class TracesSampler {
  private static final @NotNull Double DEFAULT_TRACES_SAMPLE_RATE = 1.0;

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

    final @Nullable Double tracesSampleRateFromOptions = options.getTracesSampleRate();
    final @Nullable Boolean isEnableTracing = options.getEnableTracing();
    final @Nullable Double defaultSampleRate =
        Boolean.TRUE.equals(isEnableTracing) ? DEFAULT_TRACES_SAMPLE_RATE : null;
    final @Nullable Double tracesSampleRateOrDefault =
        tracesSampleRateFromOptions == null ? defaultSampleRate : tracesSampleRateFromOptions;
    final @NotNull Double downsampleFactor =
        Math.pow(2, options.getBackpressureMonitor().getDownsampleFactor());
    final @Nullable Double downsampledTracesSampleRate =
        tracesSampleRateOrDefault == null ? null : tracesSampleRateOrDefault / downsampleFactor;

    if (downsampledTracesSampleRate != null) {
      return new TracesSamplingDecision(
          sample(downsampledTracesSampleRate),
          downsampledTracesSampleRate,
          profilesSampled,
          profilesSampleRate);
    }

    return new TracesSamplingDecision(false, null, false, null);
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
