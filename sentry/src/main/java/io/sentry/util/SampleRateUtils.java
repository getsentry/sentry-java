package io.sentry.util;

import io.sentry.TracesSamplingDecision;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SampleRateUtils {

  public static boolean isValidSampleRate(@Nullable Double sampleRate) {
    return isValidRate(sampleRate, true);
  }

  public static boolean isValidTracesSampleRate(@Nullable Double tracesSampleRate) {
    return isValidTracesSampleRate(tracesSampleRate, true);
  }

  public static boolean isValidTracesSampleRate(
      @Nullable Double tracesSampleRate, boolean allowNull) {
    return isValidRate(tracesSampleRate, allowNull);
  }

  public static boolean isValidProfilesSampleRate(@Nullable Double profilesSampleRate) {
    return isValidRate(profilesSampleRate, true);
  }

  // TODO test
  @SuppressWarnings("ObjectToString")
  public static @NotNull Double backfilledSampleRand(
      final @Nullable Double sampleRand,
      final @Nullable Double sampleRate,
      final @Nullable Boolean sampled) {
    if (sampleRand != null) {
      return sampleRand;
    }

    new RuntimeException(
            "backfilling sample rand " + sampleRand + " rate " + sampleRate + " sampled " + sampled)
        .printStackTrace();

    double newSampleRand = SentryRandom.current().nextDouble();
    if (sampleRate != null && sampled != null) {
      if (sampled) {
        return newSampleRand * sampleRate;
      } else {
        return sampleRate + (newSampleRand * (1 - sampleRate));
      }
    }

    return newSampleRand;
  }

  // TODO test
  @SuppressWarnings("ObjectToString")
  public static @NotNull TracesSamplingDecision backfilledSampleRand(
      final @NotNull TracesSamplingDecision samplingDecision) {
    if (samplingDecision.getSampleRand() != null) {
      return samplingDecision;
    }

    final @NotNull Double sampleRand =
        backfilledSampleRand(null, samplingDecision.getSampleRate(), samplingDecision.getSampled());
    return new TracesSamplingDecision(
        samplingDecision.getSampled(),
        samplingDecision.getSampleRate(),
        sampleRand,
        samplingDecision.getProfileSampled(),
        samplingDecision.getProfileSampleRate());
  }

  private static boolean isValidRate(final @Nullable Double rate, final boolean allowNull) {
    if (rate == null) {
      return allowNull;
    }
    return !rate.isNaN() && rate >= 0.0 && rate <= 1.0;
  }
}
