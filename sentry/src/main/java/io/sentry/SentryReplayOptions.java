package io.sentry;

import io.sentry.util.SampleRateUtils;
import java.util.HashSet;
import java.util.Set;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SentryReplayOptions {

  public enum SentryReplayQuality {
    /** Video Scale: 80% Bit Rate: 50.000 */
    LOW(0.8f, 50_000),

    /** Video Scale: 100% Bit Rate: 75.000 */
    MEDIUM(1.0f, 75_000),

    /** Video Scale: 100% Bit Rate: 100.000 */
    HIGH(1.0f, 100_000);

    /** The scale related to the window size (in dp) at which the replay will be created. */
    public final float sizeScale;

    /**
     * Defines the quality of the session replay. Higher bit rates have better replay quality, but
     * also affect the final payload size to transfer, defaults to 40kbps.
     */
    public final int bitRate;

    SentryReplayQuality(final float sizeScale, final int bitRate) {
      this.sizeScale = sizeScale;
      this.bitRate = bitRate;
    }
  }

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
   * Redact all text content. Draws a rectangle of text bounds with text color on top. By default
   * only views extending TextView are redacted.
   *
   * <p>Default is enabled.
   */
  private boolean redactAllText = true;

  /**
   * Redact all image content. Draws a rectangle of image bounds with image's dominant color on top.
   * By default only views extending ImageView with BitmapDrawable or custom Drawable type are
   * redacted. ColorDrawable, InsetDrawable, VectorDrawable are all considered non-PII, as they come
   * from the apk.
   *
   * <p>Default is enabled.
   */
  private boolean redactAllImages = true;

  /**
   * Redact all views with the specified class names. The class name is the fully qualified class
   * name of the view, e.g. android.widget.TextView.
   *
   * <p>Default is empty.
   */
  private Set<String> redactClasses = new HashSet<>();

  /**
   * Defines the quality of the session replay. The higher the quality, the more accurate the replay
   * will be, but also more data to transfer and more CPU load, defaults to MEDIUM.
   */
  private SentryReplayQuality quality = SentryReplayQuality.MEDIUM;

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

  public boolean getRedactAllText() {
    return redactAllText;
  }

  public void setRedactAllText(final boolean redactAllText) {
    this.redactAllText = redactAllText;
  }

  public boolean getRedactAllImages() {
    return redactAllImages;
  }

  public void setRedactAllImages(final boolean redactAllImages) {
    this.redactAllImages = redactAllImages;
  }

  public Set<String> getRedactClasses() {
    return this.redactClasses;
  }

  public void setRedactClasses(final Set<String> redactClasses) {
    this.redactClasses = redactClasses;
  }

  @ApiStatus.Internal
  public @NotNull SentryReplayQuality getQuality() {
    return quality;
  }

  public void setQuality(final @NotNull SentryReplayQuality quality) {
    this.quality = quality;
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
