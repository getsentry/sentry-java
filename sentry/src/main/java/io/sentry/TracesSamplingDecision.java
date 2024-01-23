package io.sentry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TracesSamplingDecision {

  private final @NotNull Boolean sampled;
  private final @Nullable Double sampleRate;
  private final @NotNull Boolean profileSampled;
  private final @Nullable Double profileSampleRate;

  public TracesSamplingDecision(final @NotNull Boolean sampled) {
    this(sampled, null);
  }

  public TracesSamplingDecision(final @NotNull Boolean sampled, final @Nullable Double sampleRate) {
    this(sampled, sampleRate, false, null);
  }

  public TracesSamplingDecision(
      final @NotNull Boolean sampled,
      final @Nullable Double sampleRate,
      final @NotNull Boolean profileSampled,
      final @Nullable Double profileSampleRate) {
    this.sampled = sampled;
    this.sampleRate = sampleRate;
    // A profile can be sampled only if the transaction is sampled
    this.profileSampled = sampled && profileSampled;
    this.profileSampleRate = profileSampleRate;
  }

  public @NotNull Boolean getSampled() {
    return sampled;
  }

  public @Nullable Double getSampleRate() {
    return sampleRate;
  }

  public @NotNull Boolean getProfileSampled() {
    return profileSampled;
  }

  public @Nullable Double getProfileSampleRate() {
    return profileSampleRate;
  }
}
