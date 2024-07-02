package io.sentry.opentelemetry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.trace.samplers.SamplingDecision;
import io.opentelemetry.sdk.trace.samplers.SamplingResult;
import io.sentry.TracesSamplingDecision;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class SentrySamplingResult implements SamplingResult {
  private final TracesSamplingDecision sentryDecision;

  public SentrySamplingResult(final @NotNull TracesSamplingDecision sentryDecision) {
    this.sentryDecision = sentryDecision;
  }

  @Override
  public SamplingDecision getDecision() {
    if (sentryDecision.getSampled()) {
      return SamplingDecision.RECORD_AND_SAMPLE;
    } else {
      return SamplingDecision.RECORD_ONLY;
    }
  }

  @Override
  public Attributes getAttributes() {
    return Attributes.builder()
        .put(InternalSemanticAttributes.SAMPLED, sentryDecision.getSampled())
        .put(InternalSemanticAttributes.SAMPLE_RATE, sentryDecision.getSampleRate())
        .put(InternalSemanticAttributes.PROFILE_SAMPLED, sentryDecision.getProfileSampled())
        .put(InternalSemanticAttributes.PROFILE_SAMPLE_RATE, sentryDecision.getProfileSampleRate())
        .build();
  }

  public TracesSamplingDecision getSentryDecision() {
    return sentryDecision;
  }
}
