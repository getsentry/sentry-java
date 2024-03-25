package io.sentry;

import org.jetbrains.annotations.NotNull;

public final class ExperimentalOptions {
  private @NotNull SessionReplayOptions sessionReplayOptions = new SessionReplayOptions();

  @NotNull
  public SessionReplayOptions getSessionReplayOptions() {
    return sessionReplayOptions;
  }

  public void setSessionReplayOptions(final @NotNull SessionReplayOptions sessionReplayOptions) {
    this.sessionReplayOptions = sessionReplayOptions;
  }
}
