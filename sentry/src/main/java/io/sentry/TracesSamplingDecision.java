package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class TracesSamplingDecision {

  private final @NotNull Boolean sampled;
  private final @Nullable Double sampleRate;

  public TracesSamplingDecision(@NotNull Boolean sampled, @Nullable Double sampleRate) {
    this.sampled = sampled;
    this.sampleRate = sampleRate;
  }

  public TracesSamplingDecision(@NotNull Boolean sampled) {
    this(sampled, null);
  }

  public @NotNull Boolean getSampled() {
    return sampled;
  }

  public @Nullable Double getSampleRate() {
    return sampleRate;
  }
}
