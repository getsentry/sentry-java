package io.sentry;

import org.jetbrains.annotations.NotNull;

/**
 * Experimental options for new features, these options are going to be promoted to SentryOptions
 * before GA
 */
public final class ExperimentalOptions {
  private @NotNull SentryReplayOptions sessionReplayOptions = new SentryReplayOptions();

  @NotNull
  public SentryReplayOptions getSessionReplayOptions() {
    return sessionReplayOptions;
  }

  public void setSessionReplayOptions(final @NotNull SentryReplayOptions sessionReplayOptions) {
    this.sessionReplayOptions = sessionReplayOptions;
  }
}
