package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

public final class SessionReplayOptions {

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
   * also affect the final payload size to transfer. The default value is 20kbps;
   */
  private int bitRate = 20_000;

  /**
   * Number of frames per second of the replay. The bigger the number, the more accurate the replay
   * will be, but also more data to transfer and more CPU load.
   */
  private int frameRate = 1;

  /** The maximum duration of replays for error events. */
  private long errorReplayDuration = 30_000L;

  /** The maximum duration of the segment of a session replay. */
  private long sessionSegmentDuration = 5000L;

  public SessionReplayOptions() {}

  public SessionReplayOptions(
      final @Nullable Double sessionSampleRate, final @Nullable Double errorSampleRate) {
    this.sessionSampleRate = sessionSampleRate;
    this.errorSampleRate = errorSampleRate;
  }

  @Nullable
  public Double getErrorSampleRate() {
    return errorSampleRate;
  }

  public void setErrorSampleRate(final @Nullable Double errorSampleRate) {
    this.errorSampleRate = errorSampleRate;
  }

  @Nullable
  public Double getSessionSampleRate() {
    return sessionSampleRate;
  }

  public void setSessionSampleRate(final @Nullable Double sessionSampleRate) {
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
}
