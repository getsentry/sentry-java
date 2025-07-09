package io.sentry;

import io.sentry.protocol.SdkVersion;
import io.sentry.util.SampleRateUtils;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SentryReplayOptions {

  public static final String TEXT_VIEW_CLASS_NAME = "android.widget.TextView";
  public static final String IMAGE_VIEW_CLASS_NAME = "android.widget.ImageView";
  public static final String WEB_VIEW_CLASS_NAME = "android.webkit.WebView";
  public static final String VIDEO_VIEW_CLASS_NAME = "android.widget.VideoView";
  public static final String ANDROIDX_MEDIA_VIEW_CLASS_NAME = "androidx.media3.ui.PlayerView";
  public static final String EXOPLAYER_CLASS_NAME = "com.google.android.exoplayer2.ui.PlayerView";
  public static final String EXOPLAYER_STYLED_CLASS_NAME =
      "com.google.android.exoplayer2.ui.StyledPlayerView";

  public enum SentryReplayQuality {
    /** Video Scale: 80% Bit Rate: 50.000 JPEG Compression: 10 */
    LOW(0.8f, 50_000, 10),

    /** Video Scale: 100% Bit Rate: 75.000 JPEG Compression: 30 */
    MEDIUM(1.0f, 75_000, 30),

    /** Video Scale: 100% Bit Rate: 100.000 JPEG Compression: 50 */
    HIGH(1.0f, 100_000, 50);

    /** The scale related to the window size (in dp) at which the replay will be created. */
    public final float sizeScale;

    /**
     * Defines the quality of the session replay. Higher bit rates have better replay quality, but
     * also affect the final payload size to transfer, defaults to 40kbps.
     */
    public final int bitRate;

    /** Defines the compression quality with which the screenshots are stored to disk. */
    public final int screenshotQuality;

    SentryReplayQuality(final float sizeScale, final int bitRate, final int screenshotQuality) {
      this.sizeScale = sizeScale;
      this.bitRate = bitRate;
      this.screenshotQuality = screenshotQuality;
    }

    public @NotNull String serializedName() {
      return name().toLowerCase(Locale.ROOT);
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
  private @Nullable Double onErrorSampleRate;

  /**
   * Mask all views with the specified class names. The class name is the fully qualified class name
   * of the view, e.g. android.widget.TextView. The subclasses of the specified classes will be
   * masked as well.
   *
   * <p>If you're using an obfuscation tool, make sure to add the respective proguard rules to keep
   * the class names.
   *
   * <p>Default is empty.
   */
  private Set<String> maskViewClasses = new CopyOnWriteArraySet<>();

  /**
   * Ignore all views with the specified class names from masking. The class name is the fully
   * qualified class name of the view, e.g. android.widget.TextView. The subclasses of the specified
   * classes will be ignored as well.
   *
   * <p>If you're using an obfuscation tool, make sure to add the respective proguard rules to keep
   * the class names.
   *
   * <p>Default is empty.
   */
  private Set<String> unmaskViewClasses = new CopyOnWriteArraySet<>();

  /** The class name of the view container that masks all of its children. */
  private @Nullable String maskViewContainerClass = null;

  /** The class name of the view container that unmasks its direct children. */
  private @Nullable String unmaskViewContainerClass = null;

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

  /**
   * Whether to track orientation changes in session replay. Used in Flutter as it has its own
   * callbacks to determine the orientation change.
   */
  private boolean trackOrientationChange = true;

  /**
   * SdkVersion object that contains the Sentry Client Name and its version. This object is only
   * applied to {@link SentryReplayEvent}s.
   */
  private @Nullable SdkVersion sdkVersion;

  public SentryReplayOptions(final boolean empty, final @Nullable SdkVersion sdkVersion) {
    if (!empty) {
      setMaskAllText(true);
      setMaskAllImages(true);
      maskViewClasses.add(WEB_VIEW_CLASS_NAME);
      maskViewClasses.add(VIDEO_VIEW_CLASS_NAME);
      maskViewClasses.add(ANDROIDX_MEDIA_VIEW_CLASS_NAME);
      maskViewClasses.add(EXOPLAYER_CLASS_NAME);
      maskViewClasses.add(EXOPLAYER_STYLED_CLASS_NAME);
      this.sdkVersion = sdkVersion;
    }
  }

  public SentryReplayOptions(
      final @Nullable Double sessionSampleRate,
      final @Nullable Double onErrorSampleRate,
      final @Nullable SdkVersion sdkVersion) {
    this(false, sdkVersion);
    this.sessionSampleRate = sessionSampleRate;
    this.onErrorSampleRate = onErrorSampleRate;
    this.sdkVersion = sdkVersion;
  }

  @Nullable
  public Double getOnErrorSampleRate() {
    return onErrorSampleRate;
  }

  public boolean isSessionReplayEnabled() {
    return (getSessionSampleRate() != null && getSessionSampleRate() > 0);
  }

  public void setOnErrorSampleRate(final @Nullable Double onErrorSampleRate) {
    if (!SampleRateUtils.isValidSampleRate(onErrorSampleRate)) {
      throw new IllegalArgumentException(
          "The value "
              + onErrorSampleRate
              + " is not valid. Use null to disable or values >= 0.0 and <= 1.0.");
    }
    this.onErrorSampleRate = onErrorSampleRate;
  }

  @Nullable
  public Double getSessionSampleRate() {
    return sessionSampleRate;
  }

  public boolean isSessionReplayForErrorsEnabled() {
    return (getOnErrorSampleRate() != null && getOnErrorSampleRate() > 0);
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

  /**
   * Mask all text content. Draws a rectangle of text bounds with text color on top. By default only
   * views extending TextView are masked.
   *
   * <p>Default is enabled.
   */
  public void setMaskAllText(final boolean maskAllText) {
    if (maskAllText) {
      addMaskViewClass(TEXT_VIEW_CLASS_NAME);
      unmaskViewClasses.remove(TEXT_VIEW_CLASS_NAME);
    } else {
      addUnmaskViewClass(TEXT_VIEW_CLASS_NAME);
      maskViewClasses.remove(TEXT_VIEW_CLASS_NAME);
    }
  }

  /**
   * Mask all image content. Draws a rectangle of image bounds with image's dominant color on top.
   * By default only views extending ImageView with BitmapDrawable or custom Drawable type are
   * masked. ColorDrawable, InsetDrawable, VectorDrawable are all considered non-PII, as they come
   * from the apk.
   *
   * <p>Default is enabled.
   */
  public void setMaskAllImages(final boolean maskAllImages) {
    if (maskAllImages) {
      addMaskViewClass(IMAGE_VIEW_CLASS_NAME);
      unmaskViewClasses.remove(IMAGE_VIEW_CLASS_NAME);
    } else {
      addUnmaskViewClass(IMAGE_VIEW_CLASS_NAME);
      maskViewClasses.remove(IMAGE_VIEW_CLASS_NAME);
    }
  }

  @NotNull
  public Set<String> getMaskViewClasses() {
    return this.maskViewClasses;
  }

  public void addMaskViewClass(final @NotNull String className) {
    this.maskViewClasses.add(className);
  }

  @NotNull
  public Set<String> getUnmaskViewClasses() {
    return this.unmaskViewClasses;
  }

  public void addUnmaskViewClass(final @NotNull String className) {
    this.unmaskViewClasses.add(className);
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

  @ApiStatus.Internal
  public void setMaskViewContainerClass(@NotNull String containerClass) {
    addMaskViewClass(containerClass);
    maskViewContainerClass = containerClass;
  }

  @ApiStatus.Internal
  public void setUnmaskViewContainerClass(@NotNull String containerClass) {
    unmaskViewContainerClass = containerClass;
  }

  @ApiStatus.Internal
  public @Nullable String getMaskViewContainerClass() {
    return maskViewContainerClass;
  }

  @ApiStatus.Internal
  public @Nullable String getUnmaskViewContainerClass() {
    return unmaskViewContainerClass;
  }

  @ApiStatus.Internal
  public boolean isTrackOrientationChange() {
    return trackOrientationChange;
  }

  @ApiStatus.Internal
  public void setTrackOrientationChange(final boolean trackOrientationChange) {
    this.trackOrientationChange = trackOrientationChange;
  }

  @ApiStatus.Internal
  public @Nullable SdkVersion getSdkVersion() {
    return sdkVersion;
  }

  @ApiStatus.Internal
  public void setSdkVersion(final @Nullable SdkVersion sdkVersion) {
    this.sdkVersion = sdkVersion;
  }
}
