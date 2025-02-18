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
   * Indicates the percentage in which the profiles for the session will be created. Specifying 0
   * means never, 1.0 means always. The value needs to be >= 0.0 and <= 1.0 The default is 0
   * (disabled).
   */
  private double profileSessionSampleRate = 0.0;

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

  public double getProfileSessionSampleRate() {
    return profileSessionSampleRate;
  }

  @ApiStatus.Experimental
  public void setProfileSessionSampleRate(final double profileSessionSampleRate) {
    if (!SampleRateUtils.isValidContinuousProfilesSampleRate(profileSessionSampleRate)) {
      throw new IllegalArgumentException(
          "The value "
              + profileSessionSampleRate
              + " is not valid. Use values between 0.0 and 1.0.");
    }
    this.profileSessionSampleRate = profileSessionSampleRate;
  }
}
