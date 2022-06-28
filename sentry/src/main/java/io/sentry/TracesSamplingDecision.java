package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class TracesSamplingDecision {

  private final @NotNull Boolean sampled;
  private final @NotNull Double sampleRate;

  public TracesSamplingDecision(@NotNull Boolean sampled, @Nullable Double sampleRate) {
    this.sampled = sampled;
    if (sampleRate != null) {
      this.sampleRate = sampleRate;
    } else {
      this.sampleRate = sampled ? 1.0 : 0.0;
    }
  }

  public TracesSamplingDecision(@NotNull Boolean sampled) {
    this(sampled, null);
  }

  public @NotNull Boolean getSampled() {
    return sampled;
  }

  public @NotNull Double getSampleRate() {
    return sampleRate;
  }
}
