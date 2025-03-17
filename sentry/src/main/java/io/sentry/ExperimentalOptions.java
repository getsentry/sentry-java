package io.sentry;

import io.sentry.protocol.SdkVersion;
import io.sentry.util.SampleRateUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Experimental options for new features, these options are going to be promoted to SentryOptions
 * before GA.
 *
 * <p>Beware that experimental options can change at any time.
 */
public final class ExperimentalOptions {

  /**
   * Indicates the percentage in which the profiles for the session will be created. Specifying 0
   * means never, 1.0 means always. The value needs to be >= 0.0 and <= 1.0 The default is null
   * (disabled).
   */
  private @Nullable Double profileSessionSampleRate;

  /**
   * Whether the profiling lifecycle is controlled manually or based on the trace lifecycle.
   * Defaults to {@link ProfileLifecycle#MANUAL}.
   */
  private @NotNull ProfileLifecycle profileLifecycle = ProfileLifecycle.MANUAL;

  /**
   * Whether profiling can automatically be started as early as possible during the app lifecycle,
   * to capture more of app startup. If {@link ExperimentalOptions#profileLifecycle} is {@link
   * ProfileLifecycle#MANUAL} Profiling is started automatically on startup and stopProfileSession
   * must be called manually whenever the app startup is completed If {@link
   * ExperimentalOptions#profileLifecycle} is {@link ProfileLifecycle#TRACE} Profiling is started
   * automatically on startup, and will automatically be stopped when the root span that is
   * associated with app startup ends
   */
  private boolean startProfilerOnAppStart = false;

  public ExperimentalOptions(final boolean empty, final @Nullable SdkVersion sdkVersion) {}

  /**
   * Returns whether the profiling cycle is controlled manually or based on the trace lifecycle.
   * Defaults to {@link ProfileLifecycle#MANUAL}.
   *
   * @return the profile lifecycle
   */
  @ApiStatus.Experimental
  public @NotNull ProfileLifecycle getProfileLifecycle() {
    return profileLifecycle;
  }

  /** Sets the profiling lifecycle. */
  @ApiStatus.Experimental
  public void setProfileLifecycle(final @NotNull ProfileLifecycle profileLifecycle) {
    // TODO (when moved to SentryOptions): we should log a message if the user sets this to TRACE
    // and tracing is disabled
    this.profileLifecycle = profileLifecycle;
  }

  @ApiStatus.Experimental
  public @Nullable Double getProfileSessionSampleRate() {
    return profileSessionSampleRate;
  }

  @ApiStatus.Experimental
  public void setProfileSessionSampleRate(final @Nullable Double profileSessionSampleRate) {
    if (!SampleRateUtils.isValidContinuousProfilesSampleRate(profileSessionSampleRate)) {
      throw new IllegalArgumentException(
          "The value "
              + profileSessionSampleRate
              + " is not valid. Use values between 0.0 and 1.0.");
    }
    this.profileSessionSampleRate = profileSessionSampleRate;
  }

  @ApiStatus.Experimental
  public boolean isStartProfilerOnAppStart() {
    return startProfilerOnAppStart;
  }

  @ApiStatus.Experimental
  public void setStartProfilerOnAppStart(boolean startProfilerOnAppStart) {
    this.startProfilerOnAppStart = startProfilerOnAppStart;
  }
}
