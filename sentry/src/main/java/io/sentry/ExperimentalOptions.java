package io.sentry;

import org.jetbrains.annotations.NotNull;

public final class ExperimentalOptions {
  private @NotNull SentryReplayOptions replayOptions = new SentryReplayOptions();

  @NotNull
  public SentryReplayOptions getReplayOptions() {
    return replayOptions;
  }

  public void setReplayOptions(final @NotNull SentryReplayOptions replayOptions) {
    this.replayOptions = replayOptions;
  }
}
