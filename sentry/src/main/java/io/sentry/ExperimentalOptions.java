package io.sentry;

import io.sentry.util.SampleRateUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Experimental options for new features, these options are going to be promoted to SentryOptions
 * before GA.
 *
 * <p>Beware that experimental options can change at any time.
 */
public final class ExperimentalOptions {
  private @NotNull SentryReplayOptions sessionReplay;

  /**
   * Configures the continuous profiling sample rate as a percentage of profiles to be sent in the
   * range of 0.0 to 1.0. if 1.0 is set it means that 100% of profiles will be sent. If set to 0.1
   * only 10% of profiles will be sent. Profiles are picked randomly. Default is 1 (100%).
   * ProfilesSampleRate takes precedence over this. To enable continuous profiling, don't set
   * profilesSampleRate or profilesSampler, or set them to null.
   */
  private double continuousProfilesSampleRate = 1.0;

  public ExperimentalOptions(final boolean empty) {
    this.sessionReplay = new SentryReplayOptions(empty);
  }

  @NotNull
  public SentryReplayOptions getSessionReplay() {
    return sessionReplay;
  }

  public void setSessionReplay(final @NotNull SentryReplayOptions sessionReplayOptions) {
    this.sessionReplay = sessionReplayOptions;
  }

  public double getContinuousProfilesSampleRate() {
    return continuousProfilesSampleRate;
  }

  @ApiStatus.Experimental
  public void setContinuousProfilesSampleRate(final double continuousProfilesSampleRate) {
    if (!SampleRateUtils.isValidContinuousProfilesSampleRate(continuousProfilesSampleRate)) {
      throw new IllegalArgumentException(
          "The value "
              + continuousProfilesSampleRate
              + " is not valid. Use values between 0.0 and 1.0.");
    }
    this.continuousProfilesSampleRate = continuousProfilesSampleRate;
  }
}
