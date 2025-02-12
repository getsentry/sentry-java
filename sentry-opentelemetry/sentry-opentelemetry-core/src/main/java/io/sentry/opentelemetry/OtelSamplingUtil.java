package io.sentry.opentelemetry;

import io.opentelemetry.api.common.Attributes;
import io.sentry.TracesSamplingDecision;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class OtelSamplingUtil {

  public static @Nullable TracesSamplingDecision extractSamplingDecision(
      final @NotNull Attributes attributes) {
    final @Nullable Boolean sampled = attributes.get(InternalSemanticAttributes.SAMPLED);
    if (sampled != null) {
      final @Nullable Double sampleRate = attributes.get(InternalSemanticAttributes.SAMPLE_RATE);
      final @Nullable Double sampleRand = attributes.get(InternalSemanticAttributes.SAMPLE_RAND);
      final @Nullable Boolean profileSampled =
          attributes.get(InternalSemanticAttributes.PROFILE_SAMPLED);
      final @Nullable Double profileSampleRate =
          attributes.get(InternalSemanticAttributes.PROFILE_SAMPLE_RATE);

      return new TracesSamplingDecision(
          sampled,
          sampleRate,
          sampleRand,
          profileSampled == null ? false : profileSampled,
          profileSampleRate);
    } else {
      return null;
    }
  }
}
