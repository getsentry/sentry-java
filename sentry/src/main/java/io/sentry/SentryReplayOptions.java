package io.sentry;

import io.sentry.util.SampleRateUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

public final class SentryReplayOptions {

  /**
   * Indicates the percentage in which the replay for the session will be created. Specifying 0
   * means never, 1.0 means always. The value needs to be >= 0.0 and <= 1.0 The default is null
   * (disabled).
   */
  private @Nullable Double sessionSampleRate;

  /**
   * Indicates the percentage in which a 30 seconds replay will be send with error events.
   * Specifying 0 means never, 1.0 means always. The value needs to be >= 0.0 and <= 1.0. The
   * default is null (disabled).
   */
  private @Nullable Double errorSampleRate;

  /**
   * Defines the quality of the session replay. Higher bit rates have better replay quality, but
   * also affect the final payload size to transfer, defaults to 100kbps.
   */
  private int bitRate = 100_000;

  /**
   * Number of frames per second of the replay. The bigger the number, the more accurate the replay
   * will be, but also more data to transfer and more CPU load, defaults to 1fps.
   */
  private int frameRate = 1;

  /** The maximum duration of replays for error events, defaults to 30s. */
  private long errorReplayDuration = 30_000L;

  /** The maximum duration of the segment of a session replay, defaults to 5s. */
  private long sessionSegmentDuration = 5000L;

  /** The maximum duration of a full session replay, defaults to 1h. */
  private long sessionDuration = 60 * 60 * 1000L;

  public SentryReplayOptions() {}

  public SentryReplayOptions(
      final @Nullable Double sessionSampleRate, final @Nullable Double errorSampleRate) {
    this.sessionSampleRate = sessionSampleRate;
    this.errorSampleRate = errorSampleRate;
  }

  @Nullable
  public Double getErrorSampleRate() {
    return errorSampleRate;
  }

  public boolean isSessionReplayEnabled() {
    return (getSessionSampleRate() != null && getSessionSampleRate() > 0);
  }

  public void setErrorSampleRate(final @Nullable Double errorSampleRate) {
    if (!SampleRateUtils.isValidSampleRate(errorSampleRate)) {
      throw new IllegalArgumentException(
          "The value "
              + errorSampleRate
              + " is not valid. Use null to disable or values >= 0.0 and <= 1.0.");
    }
    this.errorSampleRate = errorSampleRate;
  }

  @Nullable
  public Double getSessionSampleRate() {
    return sessionSampleRate;
  }

  public boolean isSessionReplayForErrorsEnabled() {
    return (getErrorSampleRate() != null && getErrorSampleRate() > 0);
  }

  public void setSessionSampleRate(final @Nullable Double sessionSampleRate) {
    if (!SampleRateUtils.isValidSampleRate(sessionSampleRate)) {
      throw new IllegalArgumentException(
          "The value "
              + sessionSampleRate
              + " is not valid. Use null to disable or values >= 0.0 and <= 1.0.");
    }
    this.sessionSampleRate = sessionSampleRate;
  }

  @ApiStatus.Internal
  public int getBitRate() {
    return bitRate;
  }

  @ApiStatus.Internal
  public int getFrameRate() {
    return frameRate;
  }

  @ApiStatus.Internal
  public long getErrorReplayDuration() {
    return errorReplayDuration;
  }

  @ApiStatus.Internal
  public long getSessionSegmentDuration() {
    return sessionSegmentDuration;
  }

  @ApiStatus.Internal
  public long getSessionDuration() {
    return sessionDuration;
  }
}
